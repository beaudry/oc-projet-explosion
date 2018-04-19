import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.BoolVar;
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
        final IntVar id;
        final IntVar tvPopularity;
        final IntVar livePopularity;

        Team(IntVar id, IntVar tvPopularity, IntVar livePopularity) {
            this.id = id;
            this.tvPopularity = tvPopularity;
            this.livePopularity = livePopularity;
        }
    }

    static class Match {
        final int id;
        final IntVar[] teamsIds;
        final BoolVar isShownOnTv;

        Match(int id, BoolVar isShownOnTv, IntVar[] teamsIds) {
            this.id = id;
            this.isShownOnTv = isShownOnTv;
            this.teamsIds = teamsIds;
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
                        model.intVar(teamIndex + 1),
                        model.intVar(Integer.valueOf(teamParameters[0])),
                        model.intVar(Integer.valueOf(teamParameters[1]))
                );
            }


            Match[] matches = new Match[MATCHES_PLAYED_BY_EACH_TEAM * teams.length / TEAMS_PER_MATCH];
            for (int matchIndex = 0; matchIndex < matches.length; matchIndex++) {
                matches[matchIndex] = new Match(
                        matchIndex + 1,
                        model.boolVar(),
                        model.intVarArray(TEAMS_PER_MATCH, 0, teams.length)
                );
            }


            Match[][] calendar = new Match[matches.length / MATCHES_PER_DAY][MATCHES_PER_DAY];
            for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                System.arraycopy(matches, dayNumber * calendar[dayNumber].length, calendar[dayNumber], 0, calendar[dayNumber].length);
            }

            // Avoir des équipes différentes à chaque match (et triées)
            for (Match match : matches) {
                for (int teamNumber = 0; teamNumber < match.teamsIds.length - 1; teamNumber++) {
                    model.arithm(match.teamsIds[teamNumber], "<", match.teamsIds[teamNumber + 1]).post();
                }

                for (Match otherMatch : matches) {
                    if (match != otherMatch) {
                        Constraint[] differents = new Constraint[match.teamsIds.length];
                        for (int teamIndex = 0; teamIndex < match.teamsIds.length; teamIndex++) {
                            differents[teamIndex] = model.allDifferent(match.teamsIds[teamIndex], otherMatch.teamsIds[teamIndex]);
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
                            ] = calendar[i + j][k].teamsIds[l];
                        }
                    }
                }
                model.allDifferent(flatCalendarSegment).post();
            }

            // TODO: On optimise les matchs pour la télé et le live

            IntVar[] popularityDifferencePerDay = model.intVarArray(matches.length / MATCHES_PER_DAY, 0, 1200);
            IntVar[][] matchTvPopularity = model.intVarMatrix(matches.length / MATCHES_PER_DAY, MATCHES_PER_DAY, 0, 300);
            IntVar[][] matchLivePopularity = model.intVarMatrix(matches.length / MATCHES_PER_DAY, MATCHES_PER_DAY, 0, 300);

            for (int i = 0; i < matches.length / MATCHES_PER_DAY; i++)
            {
                for(int j = 0; j < MATCHES_PER_DAY; j++)
                {
                    IntVar[] tvPopularities = model.intVarArray(TEAMS_PER_MATCH, 0, 100);
                    IntVar[] livePopularities = model.intVarArray(TEAMS_PER_MATCH, 0, 100);
                    for ( int k = 0; k < teams.length; k++)
                    {
                        for ( int l = 0; l < TEAMS_PER_MATCH; l++)
                        {
                            model.ifThen(model.arithm(calendar[i][j].teamsIds[l], "=", teams[k].id),
                                    model.arithm(tvPopularities[l], "=", teams[k].tvPopularity)
                            );
                            model.ifThen(model.arithm(calendar[i][j].teamsIds[l], "=", teams[k].id),
                                    model.arithm(livePopularities[l], "=", teams[k].livePopularity)
                            );
                        }
                    }

                    model.sum(tvPopularities, "=", matchTvPopularity[i][j]).post();
                    model.sum(livePopularities, "=", matchLivePopularity[i][j]).post();
                }
                IntVar maxTvPopularity = model.intVar(0, 300);
                model.max(maxTvPopularity, matchTvPopularity[i]);
                IntVar totalTvPopularityDifferences = model.intVar(0, 600);
                IntVar[] TvPopularityDifferences = model.intVarArray(TEAMS_PER_MATCH, 0, 300);

                for (int m = 0; m < MATCHES_PER_DAY; m++)
                {
                    model.arithm(maxTvPopularity, "-", matchTvPopularity[i][m], "=", TvPopularityDifferences[m]).post();
                }

                model.sum(TvPopularityDifferences, "=", totalTvPopularityDifferences).post();

                IntVar maxLivePopularity = model.intVar(0, 300);
                model.max(maxLivePopularity, matchLivePopularity[i]);
                IntVar totalLivePopularityDifferences = model.intVar(0, 600);
                IntVar[] LivePopularityDifferences = model.intVarArray(TEAMS_PER_MATCH, 0, 300);

                for (int m = 0; m < MATCHES_PER_DAY; m++)
                {
                    model.arithm(maxLivePopularity, "-", matchLivePopularity[i][m], "=", LivePopularityDifferences[m]).post();
                }

                model.sum(LivePopularityDifferences, "=", totalLivePopularityDifferences).post();

                model.arithm(totalTvPopularityDifferences, "+", totalLivePopularityDifferences, "=",popularityDifferencePerDay[i]).post();
            }

            IntVar totalPopularityDifference = model.intVar("Différence de popularité totale", 0, 10000);
            model.sum(popularityDifferencePerDay, "=", totalPopularityDifference).post();

            /*IntVar[] matchesTvPopularity = new IntVar[matches.length];
            IntVar[] matchesLivePopularity = new IntVar[matches.length];

            for (int i = 0; i < matches.length; i++)
            {
                IntVar idteam1 = matches[i].teamsIds[0];
                IntVar idteam2 = matches[i].teamsIds[1];
                IntVar idteam3 = matches[i].teamsIds[2];
                IntVar livePopularityteam1 = teams[idteam1.getValue()].livePopularity;
                IntVar livePopularityteam2 = teams[idteam2.getValue()].livePopularity;
                IntVar livePopularityteam3 = teams[idteam3.getValue()].livePopularity;
                int totalLivePopularity = livePopularityteam1.getValue() + livePopularityteam2.getValue() + livePopularityteam3.getValue();
                matchesLivePopularity[i] = model.intVar(totalLivePopularity);
                IntVar tvPopularityteam1 = teams[idteam1.getValue()].tvPopularity;
                IntVar tvPopularityteam2 = teams[idteam2.getValue()].tvPopularity;
                IntVar tvPopularityteam3 = teams[idteam3.getValue()].tvPopularity;
                int totalTvPopularity = tvPopularityteam1.getValue() + tvPopularityteam2.getValue() + tvPopularityteam3.getValue();
                matchesTvPopularity[i] = model.intVar(totalTvPopularity);
            }*/

            Solver solver = model.getSolver();
            // TODO: Regarder voir si on peut trouver une meilleure façon de faire de la recherche (activityBasedSearch ou autre)
            solver.setGeometricalRestart(2, 2.1, new FailCounter(model, 2), 25000);

            Solution solution = solver.findOptimalSolution(totalPopularityDifference, model.MAXIMIZE);
            //Solution optimalSolution = solver.findOptimalSolution(totalLoss, Model.MINIMIZE);

            if (solution != null) {
                for (Match dayMatches[] : calendar) {
                    for (Match match : dayMatches) {
                        System.out.printf("Match #%s (%s)\n", match.id, String.join(" vs ", Arrays.stream(match.teamsIds).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)));
                    }
                }

                System.out.println();

                for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                    System.out.printf("Jour %d: ", dayNumber + 1);
                    for (Match match : calendar[dayNumber]) {
                        System.out.printf("Match #%s (%s), ", match.id, String.join(" vs ", Arrays.stream(match.teamsIds).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)));
                    }

                    System.out.println();
                }

            System.out.println();

            System.out.printf("Difference de popularite maximum: %s", solution.getIntVal(totalPopularityDifference));

            System.out.println();

            solver.printStatistics();


        } catch (java.io.IOException exception) {
            System.out.print("Le fichier n'existe pas");
        }
    }
}