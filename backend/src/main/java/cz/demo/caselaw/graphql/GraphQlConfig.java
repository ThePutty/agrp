package cz.demo.caselaw.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowServiceException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;

/** Turns infrastructure exceptions into readable GraphQL errors instead of "INTERNAL_ERROR". */
@Configuration
public class GraphQlConfig {

    @Bean
    DataFetcherExceptionResolverAdapter caselawExceptionResolver() {
        return new DataFetcherExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
                if (ex instanceof IllegalArgumentException) {
                    return error(ex, env, ErrorType.BAD_REQUEST, ex.getMessage());
                }
                if (ex instanceof WorkflowNotFoundException) {
                    return error(ex, env, ErrorType.NOT_FOUND, "Úloha nebyla nalezena.");
                }
                if (ex instanceof WorkflowServiceException) {
                    return error(ex, env, ErrorType.INTERNAL_ERROR, "Temporal není dostupný: " + ex.getMessage());
                }
                return null;
            }

            private GraphQLError error(Throwable ex, DataFetchingEnvironment env, ErrorType type, String message) {
                return GraphqlErrorBuilder.newError(env)
                        .errorType(type)
                        .message(message == null ? ex.toString() : message)
                        .build();
            }
        };
    }
}
