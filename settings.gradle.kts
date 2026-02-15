pluginManagement {
    repositories {
        // Iranian mirrors
        maven { url = uri("https://repo.salamnet.ir/repository/maven-public/") }
        maven { url = uri("https://maven.partdp.ir/repository/public/") }

        // Chinese mirrors (Aliyun)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        // Tencent mirror
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }

        // Huawei mirror
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }

        // Original repositories (fallback)
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Iranian mirrors
        maven { url = uri("https://repo.salamnet.ir/repository/maven-public/") }
        maven { url = uri("https://maven.partdp.ir/repository/public/") }

        // Chinese mirrors (Aliyun)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }

        // Tencent mirror
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }

        // Huawei mirror
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }

        // JitPack (for some libraries)
        maven { url = uri("https://jitpack.io") }

        // Original repositories (fallback)
        google()
        mavenCentral()
    }
}

rootProject.name = "MeshMessenger"
include(":app")