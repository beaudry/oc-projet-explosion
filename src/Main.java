import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.nary.automata.FA.FiniteAutomaton;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    private static final String FILE_SEPARATOR = " ";

    public static void main(String[] args) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(args[0]));
            String[] parameters = lines.get(0).split(FILE_SEPARATOR);
        } catch (java.io.IOException exception) {
            System.out.print("Le fichier n'existe pas");
        }
    }
}