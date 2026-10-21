package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.common.ErrorCode;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /** 图片上传目录，来自 hmdp.image-upload-dir / IMAGE_UPLOAD_DIR，默认 imgs/ */
    @Value("${hmdp.image-upload-dir:imgs/}")
    private String imageUploadDir;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(imageUploadDir, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        // 前端传的是「/imgs/blogs/...」这种可直接访问的路径，落盘时要剥掉 /imgs 前缀
        String relative = filename == null ? "" : filename.trim();
        if (relative.startsWith("/imgs/")) {
            relative = relative.substring("/imgs/".length());
        } else if (relative.startsWith("imgs/")) {
            relative = relative.substring("imgs/".length());
        }
        // 防目录穿越：只允许删上传目录内的文件
        if (relative.isEmpty() || relative.contains("..") || relative.startsWith("/") || relative.startsWith("\\")) {
            return Result.fail(ErrorCode.PARAM_ERROR, "非法的文件名称");
        }
        File file = new File(imageUploadDir, relative);
        if (file.isDirectory()) {
            return Result.fail(ErrorCode.PARAM_ERROR, "错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(imageUploadDir, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
