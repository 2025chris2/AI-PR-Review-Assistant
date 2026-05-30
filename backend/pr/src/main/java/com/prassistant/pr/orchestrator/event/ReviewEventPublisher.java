package com.prassistant.pr.orchestrator.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 事件发布器 — 管理 SseEmitter 生命周期并推送 Review 事件
 *
 * <p>按 taskId 分组管理多个客户端连接，支持并发推送。
 * 当 emitter 异常或完成时自动清理，避免内存泄漏。</p>
 */
@Service
public class ReviewEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventPublisher.class);

    /** 默认 SSE 超时时间（毫秒） */
    static final long DEFAULT_SSE_TIMEOUT = 360_000L; // 6 分钟

    /**
     * 按 taskId 管理的 emitter 映射
     * CopyOnWriteArrayList 保证遍历时的线程安全性
     */
    private final ConcurrentMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 为指定任务注册一个新的 SSE 连接
     *
     * @param taskId 任务 ID
     * @return SseEmitter，用于向前端推送事件
     */
    public SseEmitter register(String taskId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_SSE_TIMEOUT);

        // 注册清理回调
        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(e -> removeEmitter(taskId, emitter));

        // 添加到注册表
        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        log.debug("SSE registered task={}, active={}", taskId, countEmitters(taskId));
        return emitter;
    }

    /**
     * 推送事件到指定任务的所有连接
     *
     * <p>使用 {@link ReviewEventType#getSseName()} 作为 SSE 事件名称，</p>
     *
     * @param event Review 事件
     */
    public void publish(ReviewEvent event) {
        String taskId = event.getTaskId();
        List<SseEmitter> emitterList = emitters.get(taskId);

        if (emitterList == null || emitterList.isEmpty()) {
            log.debug("No SSE subscribers for task={}", taskId);
            return;
        }

        log.debug("SSE publish task={}, type={}, subscribers={}",
                taskId, event.getType(), emitterList.size());

        for (SseEmitter emitter : emitterList) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getType().getSseName())
                        .data(event));
            } catch (Exception e) {
                log.warn("SSE send failed task={}, removing emitter: {}",
                        taskId, e.getMessage());
                removeEmitter(taskId, emitter);
            }
        }
    }

    /**
     * 完成指定任务的所有 SSE 连接
     *
     * @param taskId 任务 ID
     */
    public void complete(String taskId) {
        List<SseEmitter> emitterList = emitters.remove(taskId);
        if (emitterList == null) return;

        log.debug("SSE complete task={}, closing {} emitters", taskId, emitterList.size());
        for (SseEmitter emitter : emitterList) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE complete ignored task={}: {}", taskId, e.getMessage());
            }
        }
    }

    /**
     * 获取指定任务的活跃连接数
     */
    public int countEmitters(String taskId) {
        List<SseEmitter> emitterList = emitters.get(taskId);
        return emitterList != null ? emitterList.size() : 0;
    }

    /**
     * 移除某个 emitter 并清理空列表
     */
    private void removeEmitter(String taskId, SseEmitter emitter) {
        List<SseEmitter> emitterList = emitters.get(taskId);
        if (emitterList == null) return;

        emitterList.remove(emitter);
        if (emitterList.isEmpty()) {
            emitters.remove(taskId, emitterList);
        }
    }
}
