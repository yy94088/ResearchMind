package cn.researchmind.activity;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            String userId,
            String module,
            String operation,
            String targetType,
            String targetId,
            String detail
    ) {
        repository.insert(
                userId,
                module,
                operation,
                targetType,
                targetId,
                detail
        );
    }

    public List<RecentActivityView> findRecent(String userId) {
        return repository.findRecent(userId, 12);
    }
}
