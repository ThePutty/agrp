package cz.demo.caselaw.graphql;

import cz.demo.caselaw.domain.DbInspect;
import cz.demo.caselaw.store.DbInspectRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/** Storage peek for the "Technická stopa" panel. */
@Controller
public class DbInspectController {

    private final DbInspectRepository repository;

    public DbInspectController(DbInspectRepository repository) {
        this.repository = repository;
    }

    @QueryMapping
    public DbInspect dbInspect(@Argument List<String> decisionIds, @Argument Integer limit) {
        List<UUID> ids = decisionIds == null ? List.of() : decisionIds.stream().map(id -> {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Neplatné ID rozhodnutí: " + id);
            }
        }).toList();
        int max = limit == null ? 8 : Math.max(1, Math.min(limit, 50));
        return repository.inspect(ids, max);
    }
}
