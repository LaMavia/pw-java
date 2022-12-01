package cp2022.solution;

public class ErrorHandling
{
    public static void panic() {
        throw new RuntimeException("panic: unexpected thread interruption");
    }
}
