package pl.rynek.engagement.reports;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(UUID reporterId, Report.TargetType targetType, UUID targetId);

    /**
     * Kolejka moderatora: otwarte od najstarszego, rozpatrzone od ostatnio zamkniętych. Filtry są opcjonalne ({@code null} to wszystkie).
     */
    @Query(value = "select * from reports where (cast(:status as varchar) is null or status = cast(:status as varchar))"
            + " and (cast(:type as varchar) is null or target_type = cast(:type as varchar))"
            + " order by case when status = 'OPEN' then created_at end asc, resolved_at desc nulls last, id"
            + " limit :limit offset :offset", nativeQuery = true)
    List<Report> findForAdmin(String status, String type, int offset, int limit);

    @Query("select r from Report r where r.targetType = :type and r.targetId = :target and r.status = 'OPEN' order by r.createdAt")
    List<Report> findOpenOn(Report.TargetType type, UUID target);

    int countByTargetTypeAndTargetId(Report.TargetType type, UUID target);
}
