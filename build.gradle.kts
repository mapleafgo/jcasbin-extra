import com.vanniktech.maven.publish.SonatypeHost

plugins {
    java
    `java-library`
    id("io.freefair.lombok") version "8.13.1"
    id("com.vanniktech.maven.publish") version "0.32.0"
}

group = "cn.mapleafgo"
version = "0.3.3"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.casbin:jcasbin:1.81.0")
    api("io.etcd:jetcd-core:0.8.5")
    implementation("cn.hutool:hutool-core:5.8.38")
    implementation("cn.hutool:hutool-db:5.8.38")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.12.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "utf-8"
}

tasks.withType<Javadoc> {
    options.encoding = "utf-8"
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, false)
    signAllPublications()

    coordinates(project.group.toString(), project.name, project.version.toString())

    pom {
        name = project.name
        description = "JCasbin 的扩充，包含 HutoolDB Adapter，Etcd Watcher"
        url = "https://github.com/mapleafgo/jcasbin-extra"
        inceptionYear = "2020"
        licenses {
            license {
                name.set("MIT License")
                url.set("./LICENSE")
            }
        }
        developers {
            developer {
                id = "mapleafgo"
                name = "慕枫"
                email = "mapleafgo@163.com"
                url = "https://github.com/mapleafgo"
            }
        }
        scm {
            url = "https://github.com/mapleafgo/jcasbin-extra"
            connection = "scm:git:https://github.com/mapleafgo/jcasbin-extra.git"
            developerConnection = "scm:git:ssh://github.com/mapleafgo/jcasbin-extra.git"
        }
    }
}
