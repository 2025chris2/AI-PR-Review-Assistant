package com.prassistant.pr.github;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitHub PR 文件列表中的单条记录
 *
 * <p>对应 GET /repos/{owner}/{repo}/pulls/{number}/files 响应中的元素。
 * 只保留业务需要的字段，其余字段通过 rawUrl 按需获取。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubPrFile {

    /** 文件名（相对路径） */
    private String filename;

    /** 变更状态：added / modified / removed / renamed */
    private String status;

    /** 新增行数 */
    private int additions;

    /** 删除行数 */
    private int deletions;

    /** 总变更行数 */
    private int changes;

    /** 单个文件的 patch 文本（可能为 null，大文件不返回 patch） */
    private String patch;

    /** 文件 raw_url */
    private String rawUrl;
}
