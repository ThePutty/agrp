package cz.demo.caselaw.graphql;

import cz.demo.caselaw.domain.JobStatus;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;

/**
 * The workflow's own state may still say RUNNING when the execution was terminated or timed out,
 * so the API overlays the authoritative status from the Temporal server.
 */
final class WorkflowStatusMapper {

    static JobStatus toJobStatus(WorkflowExecutionStatus status) {
        return switch (status) {
            case WORKFLOW_EXECUTION_STATUS_RUNNING, WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW -> JobStatus.RUNNING;
            case WORKFLOW_EXECUTION_STATUS_COMPLETED -> JobStatus.COMPLETED;
            default -> JobStatus.FAILED;
        };
    }

    /**
     * A research job can finish as a workflow (COMPLETED) while being a business failure,
     * so a FAILED state reported by the workflow itself always wins.
     */
    static JobStatus overlay(JobStatus fromState, WorkflowExecutionStatus execution) {
        JobStatus fromServer = toJobStatus(execution);
        if (fromState == JobStatus.FAILED || fromServer == JobStatus.FAILED) {
            return JobStatus.FAILED;
        }
        return fromServer;
    }

    private WorkflowStatusMapper() {}
}
