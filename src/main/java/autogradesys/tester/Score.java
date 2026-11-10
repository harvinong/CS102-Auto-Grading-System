package autogradesys.tester;

public record Score(int scored, int total) {
    @Override
    public String toString() { return scored + "/" + total; }
}