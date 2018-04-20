import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    private static final String PARAMETER_SEPARATOR = " ";
    private static final int LINE_NUMBER_TEAMS_START = 1;
    private static final int TEAMS_PER_MATCH = 3;

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
            int DAYS_BETWEEN_MATCHES = Integer.valueOf(parameters[2]);

            Team[] teams = new Team[lines.size() - 1];
            String[] teamParameters;

            for (int lineNumber = LINE_NUMBER_TEAMS_START; lineNumber < lines.size(); lineNumber++) {
                teamParameters = lines.get(lineNumber).split(PARAMETER_SEPARATOR);
                int teamIndex = lineNumber - LINE_NUMBER_TEAMS_START;

                teams[teamIndex] = new Team(
                        teamIndex + 1,
                        Integer.valueOf(teamParameters[0]),
                        Integer.valueOf(teamParameters[1])
                );
            }


            Match[] matches = new Match[MATCHES_PLAYED_BY_EACH_TEAM * teams.length / TEAMS_PER_MATCH];
            for (int matchIndex = 0; matchIndex < matches.length; matchIndex++) {
                matches[matchIndex] = new Match(
                        matchIndex + 1,
                        model.intVarArray(TEAMS_PER_MATCH, 0, teams.length),
                        model.intVarArray(TEAMS_PER_MATCH, 0, 100),
                        model.intVarArray(TEAMS_PER_MATCH, 0, 100)
                );
            }


            Match[][] calendar = new Match[matches.length / MATCHES_PER_DAY][MATCHES_PER_DAY];
            for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                System.arraycopy(matches, dayNumber * calendar[dayNumber].length, calendar[dayNumber], 0, calendar[dayNumber].length);
            }

            // Avoir des équipes différentes à chaque match (et triées)
            for (Match match : matches) {
                for (int teamNumber = 0; teamNumber < match.getTeamIds().length - 1; teamNumber++) {
                    model.arithm(match.getTeamIds()[teamNumber], "<", match.getTeamIds()[teamNumber + 1]).post();
                }

                for (Match otherMatch : matches) {
                    if (match != otherMatch) {
                        Constraint[] differents = new Constraint[match.getTeamIds().length];
                        for (int teamIndex = 0; teamIndex < match.getTeamIds().length; teamIndex++) {
                            differents[teamIndex] = model.allDifferent(match.getTeamIds()[teamIndex], otherMatch.getTeamIds()[teamIndex]);
                        }
                        model.or(differents).post();
                    }
                }
            }

            // TODO: Chaque équipe joue le même nombre de match (ce nombre est variable et sera le deuxième argument de la première ligne de instance.txt)

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

            // TODO: On optimise les matchs pour la télé et le live

            IntVar[] popularityDifferencePerDay = model.intVarArray(matches.length / MATCHES_PER_DAY, 0, 1200);
            IntVar[][] matchTvPopularity = model.intVarMatrix(matches.length / MATCHES_PER_DAY, MATCHES_PER_DAY, 0, 300);
            IntVar[][] matchLivePopularity = model.intVarMatrix(matches.length / MATCHES_PER_DAY, MATCHES_PER_DAY, 0, 300);

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

            for (int i = 0; i < calendar.length; i++) {
                for (int j = 0; j < calendar[i].length; j++) {
                    //model.arithm(tvPopularities[2], "=", matchTvPopularity[i][j]).post();
                    model.sum(calendar[i][j].getTvPopularities(), "=", matchTvPopularity[i][j]).post();
                    model.sum(calendar[i][j].getLivePopularities(), "=", matchLivePopularity[i][j]).post();
                }

                IntVar maxTvPopularity = model.intVar(0, 300);
                model.max(maxTvPopularity, matchTvPopularity[i]);
                IntVar totalTvPopularityDifferences = model.intVar(0, 600);
                IntVar[] TvPopularityDifferences = model.intVarArray(TEAMS_PER_MATCH, 0, 300);

                for (int m = 0; m < MATCHES_PER_DAY; m++) {
                    model.arithm(maxTvPopularity, "-", matchTvPopularity[i][m], "=", TvPopularityDifferences[m]).post();
                }

                model.sum(TvPopularityDifferences, "=", totalTvPopularityDifferences).post();

                IntVar maxLivePopularity = model.intVar(0, 300);
                model.max(maxLivePopularity, matchLivePopularity[i]);
                IntVar totalLivePopularityDifferences = model.intVar(0, 600);
                IntVar[] LivePopularityDifferences = model.intVarArray(TEAMS_PER_MATCH, 0, 300);

                for (int m = 0; m < MATCHES_PER_DAY; m++) {
                    model.arithm(maxLivePopularity, "-", matchLivePopularity[i][m], "=", LivePopularityDifferences[m]).post();
                }

                model.sum(LivePopularityDifferences, "=", totalLivePopularityDifferences).post();

                model.arithm(totalTvPopularityDifferences, "+", totalLivePopularityDifferences, "=",popularityDifferencePerDay[i]).post();
            }

            IntVar totalPopularityDifference = model.intVar("Différence de popularité totale", 0, 10000);
            //model.sum(popularityDifferencePerDay, "=", totalPopularityDifference).post();
            model.arithm(matchTvPopularity[0][0], "=", totalPopularityDifference).post();

            IntVar[] allVariables = Arrays.stream(matches).flatMap(match -> Arrays.stream(match.variables).flatMap(Arrays::stream)).toArray(IntVar[]::new);

            Solver solver = model.getSolver();
            // TODO: Regarder voir si on peut trouver une meilleure façon de faire de la recherche (activityBasedSearch ou autre)
            solver.setSearch(Search.activityBasedSearch(allVariables));
            solver.setGeometricalRestart(2, 2.1, new FailCounter(model, 2), 25000);

            Solution solution = solver.findOptimalSolution(totalPopularityDifference, model.MAXIMIZE);
            //Solution optimalSolution = solver.findOptimalSolution(totalLoss, Model.MINIMIZE);

            if (solution != null) {
                for (Match dayMatches[] : calendar) {
                    for (Match match : dayMatches) {
                        System.out.printf("Match #%s (%s) \n", match.id, String.join(" vs ", Arrays.stream(match.getTeamIds()).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)));
                    }
                }

                System.out.println();

                for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                    System.out.printf("Jour %d: ", dayNumber + 1);
                    for (Match match : calendar[dayNumber]) {
                        System.out.printf("Match #%s (%s), ", match.id, String.join(" vs ", Arrays.stream(match.getTeamIds()).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)));
                    }

                    System.out.println();
                }

                System.out.printf("\nDifference de popularite maximum: %s\n", solution.getIntVal(totalPopularityDifference));
            }

            System.out.println();
            solver.printStatistics();
        } catch (java.io.IOException exception) {
            System.out.print("Le fichier n'existe pas");
        }
    }
}