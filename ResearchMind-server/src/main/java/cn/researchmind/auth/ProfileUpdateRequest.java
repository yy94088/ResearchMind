package cn.researchmind.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 个字符")
        String realName,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱不能超过 100 个字符")
        String email,

        @Size(max = 200, message = "所属机构不能超过 200 个字符")
        String institution,

        @Size(max = 300, message = "研究方向不能超过 300 个字符")
        String researchDirection,

        @Size(max = 500, message = "个人简介不能超过 500 个字符")
        String bio
) {
}
