plugins {
    java
    `java-library`
    `maven-publish`
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("jcasbin extra")
                description.set("JCasbin 的扩充，包含 HutoolDB Adapter，Etcd Watcher")
            }
        }
    }
}
