package com.smartisanos.music

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import okio.Path.Companion.toOkioPath

/**
 * 应用进程入口。
 *
 * 主要职责是初始化全局图片加载器 [Coil]，为云音乐网络封面提供统一的两级缓存：
 * - 内存缓存约为可用内存的 12%，命中后列表滚动 / 转场来回不再重复解码。
 * - 磁盘缓存约为可用空间的 2%（位于 cacheDir/image_cache），命中后跨进程生命周期无需重新下载。
 *
 * 配置参考自 NeriPlayer。Coil 3 的默认网络缓存策略会优先读取已有磁盘响应，
 * 并缓存 2xx 图片响应，避免网易云图片 CDN 在页面来回切换时反复下载封面。
 */
class SmartisanMusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val imageLoader = ImageLoader.Builder(this)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.12)
                    .build()
            }
            .build()
        SingletonImageLoader.setSafe { imageLoader }
    }
}
