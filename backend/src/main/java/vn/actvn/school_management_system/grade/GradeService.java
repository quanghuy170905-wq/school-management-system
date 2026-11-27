package vn.actvn.school_management_system.grade;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.actvn.school_management_system.audit.AuditAction;
import vn.actvn.school_management_system.audit.AuditService;
import vn.actvn.school_management_system.common.exception.AppException;
import vn.actvn.school_management_system.common.util.ApiParameters;
import vn.actvn.school_management_system.grade.GradeViews.*;

@Service
public class GradeService {
    private final GradeRepository repository;
    private final AuditService audit;
    private final Clock clock;

    public GradeService(GradeRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    public ClassGrades list(long teacherId, String classId, String subjectId, String schoolYear,
                            String term, String scoreType) {
        return repository.findByClass(teacherId, ApiParameters.id(classId), ApiParameters.id(subjectId),
                ApiParameters.schoolYear(schoolYear), ApiParameters.term(term), ApiParameters.scoreType(scoreType));
    }

    @Transactional
    public Grade save(long teacherId, PutGradeRequest body) {
        long classId = ApiParameters.id(body.classId());
        long subjectId = ApiParameters.id(body.subjectId());
        long studentId = ApiParameters.id(body.studentId());
        var old = repository.findCurrent(teacherId, classId, subjectId, studentId,
                body.schoolYear(), body.term(), body.scoreType());
        String now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
        repository.upsert(teacherId, classId, subjectId, studentId, body.schoolYear(), body.term(),
                body.scoreType(), body.score(), now);
        var saved = repository.findCurrent(teacherId, classId, subjectId, studentId,
                body.schoolYear(), body.term(), body.scoreType()).orElseThrow(AppException::notFound);
        audit.record(teacherId, studentId, old.isPresent() ? AuditAction.UPDATE : AuditAction.CREATE,
                "grades", saved.id(), old.orElse(null), saved);
        return saved;
    }
}
