package vip.xiaoweisoul.sdk.demo;

import androidx.annotation.NonNull;

/**
 * 主页面角色下拉项，只保留展示名和建连所需的 soul_id。
 */
final class SoulProfileOption {
    final String name;
    final String soulId;

    SoulProfileOption(@NonNull String name, @NonNull String soulId) {
        this.name = name.trim();
        this.soulId = soulId.trim();
    }

    /**
     * 返回下拉框展示文案；名称为空时回退到 soul_id，避免空白选项。
     */
    @NonNull
    String displayName() {
        return name.isEmpty() ? soulId : name;
    }
}
