import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.search.strategy.selectors.variables.ImpactBased;
import org.chocosolver.solver.variables.IntVar;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

class Main {
    private static final String PARAMETER_SEPARATOR = " ";
    private static final int LINE_NUMBER_TEAMS_START = 1;
    private static final int TEAMS_PER_MATCH = 3;
    private static final int MIN_TEAM_POPULARITY = 1;
    private static final int MIN_MATCH_POPULARITY = MIN_TEAM_POPULARITY * TEAMS_PER_MATCH;
    private static final int MAX_TEAM_POPULARITY = 5;
    private static final int MAX_MATCH_POPULARITY = MAX_TEAM_POPULARITY * TEAMS_PER_MATCH;
    private static final int TV_MATCH_INDEX = 0;
    private static final int LIVE_MATCH_INDEX = 1;
    private static final int GYM_MATCHES_INDEX = 2;
    private static final String MATCHES_LOCATION[] = {"Télévision", "Stade", "Gymnase"};

    static class Team {
        final int id;
        final int tvPopularity;
        final int livePopularity;

        Team(int id, int tvPopularity, int livePopularity) {
            this.id = id;
            this.tvPopularity = tvPopularity;
            this.livePopularity = livePopularity;
        }
    }

    static class Match {
        final int id;
        final IntVar[][] variables;

        Match(int id, IntVar[] teamsIds, IntVar[] tvPopularities, IntVar[] livePopularities) {
            this.id = id;
            variables = new IntVar[][]{teamsIds, tvPopularities, livePopularities};
        }

        IntVar[] getTeamIds() {
            return variables[0];
        }

        IntVar[] getTvPopularities() {
            return variables[1];
        }

        IntVar[] getLivePopularities() {
            return variables[2];
        }
    }

    public static void main(String[] args) {
        try {
            Model model = new Model("KinBall");

            List<String> lines = Files.readAllLines(Paths.get(args[0]));

            String[] parameters = lines.get(0).split(" ");
            int MATCHES_PLAYED_BY_EACH_TEAM = Integer.valueOf(parameters[0]);
            int MATCHES_PER_DAY = Integer.valueOf(parameters[1]);
            int DAYS_BETWEEN_MATCHES = Integer.valueOf(parameters[2]) + 1;

            Team[] teams = new Team[lines.size() - 1];
            if (MATCHES_PLAYED_BY_EACH_TEAM * teams.length % (TEAMS_PER_MATCH * MATCHES_PER_DAY) > 0) {
                throw new IllegalArgumentException("Mauvais nombre d'équipes.");
            }

            String[] teamParameters;

            for (int lineNumber = LINE_NUMBER_TEAMS_START; lineNumber < lines.size(); lineNumber++) {
                teamParameters = lines.get(lineNumber).split(PARAMETER_SEPARATOR);
                int teamIndex = lineNumber - LINE_NUMBER_TEAMS_START;

                teams[teamIndex] = new Team(
                        teamIndex + 1,
                        Integer.valueOf(teamParameters[TV_MATCH_INDEX]),
                        Integer.valueOf(teamParameters[LIVE_MATCH_INDEX])
                );
            }

            List<IntVar> allVariables = new LinkedList<>();

            Match[] matches = new Match[MATCHES_PLAYED_BY_EACH_TEAM * teams.length / TEAMS_PER_MATCH];
            for (int matchIndex = 0; matchIndex < matches.length; matchIndex++) {
                matches[matchIndex] = new Match(
                        matchIndex + 1,
                        model.intVarArray(TEAMS_PER_MATCH, 0, teams.length),
                        model.intVarArray(TEAMS_PER_MATCH, MIN_TEAM_POPULARITY, MAX_TEAM_POPULARITY),
                        model.intVarArray(TEAMS_PER_MATCH, MIN_TEAM_POPULARITY, MAX_TEAM_POPULARITY)
                );
            }

            allVariables.addAll(Arrays.stream(matches).flatMap(match -> Arrays.stream(match.variables).flatMap(Arrays::stream)).collect(Collectors.toList()));

            Match[][] calendar = new Match[matches.length / MATCHES_PER_DAY][MATCHES_PER_DAY];
            for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                System.arraycopy(matches, dayNumber * calendar[dayNumber].length, calendar[dayNumber], 0, calendar[dayNumber].length);
            }

            for (int i = 0; i < matches.length; i++) {
                Match match = matches[i];
                for (int teamNumber = 0; teamNumber < match.getTeamIds().length - 1; teamNumber++) {
                    model.arithm(match.getTeamIds()[teamNumber], "<", match.getTeamIds()[teamNumber + 1]).post();
                }

                for (int j = i + 1; j < matches.length; j++) {
                    Match otherMatch = matches[j];

                    Constraint[] differents = new Constraint[match.getTeamIds().length];
                    for (int teamIndex = 0; teamIndex < match.getTeamIds().length; teamIndex++) {
                        differents[teamIndex] = model.allDifferent(match.getTeamIds()[teamIndex], otherMatch.getTeamIds()[teamIndex]);
                    }
                    model.or(differents).post();
                }
            }

            // Bris de symétrie
            model.arithm(calendar[0][0].getTeamIds()[0], "<=", calendar[calendar.length - 1][0].getTeamIds()[0]).post();

            // Chaque équipe joue le même nombre de match
            IntVar sameTeamLimit = model.intVar(MATCHES_PLAYED_BY_EACH_TEAM);
            IntVar[] matchesTeamsIds = Arrays.stream(matches).flatMap(match -> Arrays.stream(match.getTeamIds())).toArray(IntVar[]::new);
            for (Team team : teams) {
                model.count(team.id, matchesTeamsIds, sameTeamLimit).post();
            }

            // Une équipe a un certain nombre de jours de repos entre deux parties
            for (int i = 0; i < calendar.length - DAYS_BETWEEN_MATCHES + 1; i++) {
                IntVar[] flatCalendarSegment = new IntVar[TEAMS_PER_MATCH * MATCHES_PER_DAY * DAYS_BETWEEN_MATCHES];
                for (int j = 0; j < DAYS_BETWEEN_MATCHES; j++) {
                    for (int k = 0; k < MATCHES_PER_DAY; k++) {
                        for (int l = 0; l < TEAMS_PER_MATCH; l++) {
                            flatCalendarSegment[
                                    j * MATCHES_PER_DAY * TEAMS_PER_MATCH +
                                            k * TEAMS_PER_MATCH +
                                            l
                                    ] = calendar[i + j][k].getTeamIds()[l];
                        }
                    }
                }
                model.allDifferent(flatCalendarSegment).post();
            }

            // On optimise les matchs pour la télé et le live
            Tuples tvPopularitiesTuples = new Tuples();
            Tuples livePopularitiesTuples = new Tuples();
            for (Team team : teams) {
                tvPopularitiesTuples.add(team.id, team.tvPopularity);
                livePopularitiesTuples.add(team.id, team.livePopularity);
            }

            for (Match match : matches) {
                for (int i = 0; i < match.getTeamIds().length; i++) {
                    model.table(match.getTeamIds()[i], match.getTvPopularities()[i], tvPopularitiesTuples).post();
                    model.table(match.getTeamIds()[i], match.getLivePopularities()[i], livePopularitiesTuples).post();
                }
            }

            IntVar totalTvPopularity = model.intVar(MIN_MATCH_POPULARITY * calendar.length, MAX_MATCH_POPULARITY * calendar.length);
            model.sum(Arrays.stream(calendar).flatMap(matchesOfDay -> Arrays.stream(matchesOfDay[TV_MATCH_INDEX].getTvPopularities())).toArray(IntVar[]::new), "=", totalTvPopularity).post();

            IntVar totalLivePopularity = model.intVar(MIN_MATCH_POPULARITY * calendar.length, MAX_MATCH_POPULARITY * calendar.length);
            model.sum(Arrays.stream(calendar).flatMap(matchesOfDay -> Arrays.stream(matchesOfDay[LIVE_MATCH_INDEX].getLivePopularities())).toArray(IntVar[]::new), "=", totalLivePopularity).post();

            IntVar totalPopularity = model.intVar("Popularité totale", MIN_MATCH_POPULARITY * 2 * calendar.length, MAX_MATCH_POPULARITY * 2 * calendar.length);
            model.arithm(totalLivePopularity, "+", totalTvPopularity, "=", totalPopularity).post();

            Solver solver = model.getSolver();
            solver.setSearch(new ImpactBased(allVariables.toArray(new IntVar[0]), false));
            solver.limitTime("120s");

            Solution solution = solver.findOptimalSolution(totalPopularity, Model.MAXIMIZE);

            if (solution != null) {
                for (Match dayMatches[] : calendar) {
                    for (Match match : dayMatches) {
                        System.out.printf("Match #%s (%s) - TV: %s, Stade: %s \n",
                                match.id,
                                String.join(" vs ", Arrays.stream(match.getTeamIds()).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)),
                                Arrays.stream(match.getTvPopularities()).mapToInt(solution::getIntVal).sum(),
                                Arrays.stream(match.getLivePopularities()).mapToInt(solution::getIntVal).sum()
                        );
                    }
                }

                System.out.println();

                for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                    System.out.printf("Jour %d: ", dayNumber + 1);
                    for (int i = 0; i < calendar[dayNumber].length; i++) {
                        Match match = calendar[dayNumber][i];
                        System.out.printf("Match #%s – %s – (%s), ", match.id, MATCHES_LOCATION[Math.min(i, GYM_MATCHES_INDEX)], String.join(" vs ", Arrays.stream(match.getTeamIds()).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)));
                    }

                    System.out.println();
                }

                System.out.printf("\nPopularité télé: %s\n", solution.getIntVal(totalTvPopularity));
                System.out.printf("Popularité stade: %s\n\n", solution.getIntVal(totalLivePopularity));
                System.out.printf("Popularité totale l'horaire: %s\n", solution.getIntVal(totalPopularity));
            }

            System.out.println();
            solver.printStatistics();
        } catch (java.io.IOException exception) {
            System.out.print("Le fichier n'existe pas");
        }
    }
}