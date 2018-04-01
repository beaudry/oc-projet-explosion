import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.lang.reflect.Array;
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
        final IntVar id;
        final Team[] teams;
        final BoolVar isShownOnTv;

        public Match(IntVar id, BoolVar isShownOnTv, Team ...teams) {
            this.id = id;
            this.isShownOnTv = isShownOnTv;
            this.teams = teams;
        }
    }

    public static void main(String[] args) {
        try {
            Model model = new Model("KinBall");

            List<String> lines = Files.readAllLines(Paths.get(args[0]));

            int MATCHES_PER_DAY = Integer.valueOf(lines.get(0));
            Team[] teams = new Team[lines.size()-1];
            String[] teamParameters;
            int teamIndex;

            for (int lineNumber = LINE_NUMBER_TEAMS_START; lineNumber < lines.size(); lineNumber++) {
                teamParameters = lines.get(lineNumber).split(PARAMETER_SEPARATOR);
                teamIndex = lineNumber - LINE_NUMBER_TEAMS_START;

                teams[teamIndex] = new Team(
                        model.intVar(teamIndex + 1),
                        model.intVar(Integer.valueOf(teamParameters[0])),
                        model.intVar(Integer.valueOf(teamParameters[1]))
                );
            }

            Match[] matches = new Match[teams.length / TEAMS_PER_MATCH];
            for (int matchIndex = 0; matchIndex < matches.length; matchIndex++) {
                matches[matchIndex] = new Match(
                        model.intVar(matchIndex + 1),
                        model.boolVar(),
                        Arrays.copyOfRange(teams, matchIndex * TEAMS_PER_MATCH, (matchIndex + 1) * TEAMS_PER_MATCH - 1)
                );
            }

            Match[][] calendar = new Match[matches.length / MATCHES_PER_DAY][MATCHES_PER_DAY];
            for (int dayNumber = 0; dayNumber < calendar.length; dayNumber++) {
                calendar[dayNumber] = Arrays.copyOfRange(matches, dayNumber * MATCHES_PER_DAY, (dayNumber + 1) * MATCHES_PER_DAY - 1);
            }

            // AllDiff pour avoir des équipes différentes à chaque match

            // AllDiff pour avoir des matchs différents à chaque jour d'une semaine

            int numberOfWeeks = (matches.length / MATCHES_PER_DAY)/7;
            IntVar[][] matchesIndex = new IntVar[numberOfWeeks][7*MATCHES_PER_DAY];
            for(int k = 0; k < numberOfWeeks; k++)
            {
                for (int i = 0; i < (7*MATCHES_PER_DAY); i++)
                {
                    for (int j = 0; j < MATCHES_PER_DAY; j++)
                    {
                        matchesIndex[k][i*MATCHES_PER_DAY + j] = calendar[i][j].id;
                    }
                }
            }
            IntVar[] extraWeekMatchesIndex = new IntVar[((matches.length/MATCHES_PER_DAY)%7)*MATCHES_PER_DAY];
            for (int i = 0; i < (((matches.length/MATCHES_PER_DAY)%7)*MATCHES_PER_DAY); i++)
            {
                for (int j = 0; j < MATCHES_PER_DAY; j++)
                {
                    extraWeekMatchesIndex[i*MATCHES_PER_DAY + j] = calendar[i+(numberOfWeeks*7)][j].id;
                }
            }
            for (int i = 0; i < numberOfWeeks; i++)
            {
                model.allDifferent(matchesIndex[i]).post();
            }
            model.allDifferent(extraWeekMatchesIndex).post();


        } catch (java.io.IOException exception) {
            System.out.print("Le fichier n'existe pas");
        }
    }
}