package cp2022.solution;

public class Identification {
    public static long uid() {
        return Thread.currentThread().getId();
    }
}
