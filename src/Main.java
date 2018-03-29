import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String PARAMETER_SEPARATOR = " ";
    private static final int LINE_NUMBER_TEAMS_START = 1;

    static class Team {
        final IntVar tvPopularity;
        final IntVar livePopularity;

        public Team(IntVar tvPopularity, IntVar livePopularity) {
            this.tvPopularity = tvPopularity;
            this.livePopularity = livePopularity;
        }
    }

    public static void main(String[] args) {
        try {
            Model model = new Model("KinBall");

            List<String> lines = Files.readAllLines(Paths.get(args[0]));

            Team[] teams = new Team[lines.size()-1];
            String[] teamParameters;

            for (int lineNumber = LINE_NUMBER_TEAMS_START; lineNumber < lines.size(); lineNumber++) {
                teamParameters = lines.get(lineNumber).split(PARAMETER_SEPARATOR);
                teams[lineNumber - LINE_NUMBER_TEAMS_START] = new Team(
                        model.intVar(Integer.valueOf(teamParameters[0])),
                        model.intVar(Integer.valueOf(teamParameters[1]))
                );
            }
            
        } catch (java.io.IOException exception) {
            System.out.print("Le fichier n'existe pas");
        }
    }
}