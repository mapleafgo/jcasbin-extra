plugins {
    java
    `java-library`
    `maven-publish`
    signing
    id("io.freefair.lombok") version "4.1.6"
}

group = "cn.wenkang365t"
version = "0.1.0"

java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.casbin", "jcasbin", "1.4.0")
    api("io.etcd", "jetcd-core", "0.4.1")
    implementation("cn.hutool", "hutool-core", "5.2.2")
    implementation("cn.hutool", "hutool-db", "5.2.2")
    testImplementation("junit", "junit", "4.12")
}

tasks.withType<JavaCompile>() {
    options.encoding = "utf-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "utf-8"
}

val NEXUS_USERNAME: String by project;
val NEXUS_PASSWORD: String by project;

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
            pom {
                url.set("https://github.com/fanlide/jcasbin-extra")
                name.set(project.name)
                description.set("JCasbin 的扩充，包含 HutoolDB Adapter，Etcd Watcher")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("./LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("mufeng")
                        name.set("慕枫")
                        email.set("javakang@qq.com")
                    }
                }
                scm {
                    connection.set("https://github.com/fanlide/jcasbin-extra.git")
                    url.set(pom.url)
                }
            }
        }
        repositories {
            mavenLocal {
                url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                credentials {
                    username = NEXUS_USERNAME
                    password = NEXUS_PASSWORD
                }
            }
            mavenCentral {
                url = uri("https://oss.sonatype.org/content/repositories/snapshots")
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
