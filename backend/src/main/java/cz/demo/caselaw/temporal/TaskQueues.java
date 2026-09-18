package cz.demo.caselaw.temporal;

/** Single task queue for the whole demo; the app hosts both the API and the worker. */
public final class TaskQueues {
    public static final String CASELAW = "caselaw";

    private TaskQueues() {}
}
