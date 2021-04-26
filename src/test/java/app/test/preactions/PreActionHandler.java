package app.test.preactions;

public interface PreActionHandler {

    void execute();
    default int order() {
        return Integer.MAX_VALUE;
    }
}
