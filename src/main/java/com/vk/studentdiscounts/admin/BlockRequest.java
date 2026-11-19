package com.vk.studentdiscounts.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason причина блокировки; сохраняется в аккаунте и в журнале аудита
 */
public record BlockRequest(@NotBlank @Size(max = 500) String reason) {
}
