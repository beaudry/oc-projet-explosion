import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.search.limits.FailCounter;
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

        public Team(IntVar id, IntVar tvPopularity, IntVar livePopularity) {
            this.id = id;
            this.tvPopularity = tvPopularity;
            this.livePopularity = livePopularity;
        }
    }

    static class Match {
        final int id;
        final IntVar[] teamsIds;
        final BoolVar isShownOnTv;

        public Match(int id, BoolVar isShownOnTv, IntVar[] teamsIds) {
            this.id = id;
            this.isShownOnTv = isShownOnTv;
            this.teamsIds = teamsIds;
        }
    }

    public static void main(String[] args) {
        try {
            Model model = new Model("KinBall");

            List<String> lines = Files.readAllLines(Paths.get(args[0]));

            int MATCHES_PER_DAY = Integer.valueOf(lines.get(0));
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


            Match[] matches = new Match[teams.length / TEAMS_PER_MATCH];
            for (int matchIndex = 0; matchIndex < matches.length; matchIndex++) {
                matches[matchIndex] = new Match(
                        matchIndex + 1,
                        model.boolVar(),
                        model.intVarArray(TEAMS_PER_MATCH, 0, teams.length)
                );
            }

            IntVar[][] calendar = new IntVar[matches.length / MATCHES_PER_DAY][MATCHES_PER_DAY];
            for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                calendar[dayNumber] = model.intVarArray(MATCHES_PER_DAY, 0, matches.length);
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

            // Chaque match ne se produit qu'une seule fois dans la session.
            IntVar[] flatCalendar = new IntVar[matches.length];
            for (int i = 0; i < calendar.length; i++) {
                for (int j = 0; j < calendar[i].length; j++) {
                    flatCalendar[i * calendar[i].length + j] = calendar[i][j];
                }
            }
            model.allDifferent(flatCalendar).post();

            // TODO: Chaque équipe joue le même nombre de match (ce nombre est variable et sera le deuxième argument de la première ligne de instance.txt)

            // TODO: Les équipes ont un certain nombre de jour de repos avant de rejouer

            // TODO: On optimise les matchs pour la télé et le live

            Solver solver = model.getSolver();
            solver.setGeometricalRestart(2, 2.1, new FailCounter(model, 2), 25000);

            Solution solution = solver.findSolution();

            for (Match match : matches) {
                System.out.printf("Match #%s (%s)\n", match.id, String.join(" vs ", Arrays.stream(match.teamsIds).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)));
            }

            System.out.println();

            for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                System.out.printf("Jour %d: ", dayNumber + 1);
                for (IntVar matchId : calendar[dayNumber]) {
                    Match match = matches[solution.getIntVal(matchId)];
                    System.out.printf("Match #%s (%s), ", match.id, String.join(" vs ", Arrays.stream(match.teamsIds).map(teamId -> String.valueOf(solution.getIntVal(teamId))).toArray(String[]::new)));
                }

                System.out.println();
            }

            System.out.println();

            solver.printStatistics();


        } catch (java.io.IOException exception) {
            System.out.print("Le fichier n'existe pas");
        }
    }
}