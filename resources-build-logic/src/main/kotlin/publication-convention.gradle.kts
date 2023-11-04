/*
 * Copyright 2021 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

import java.util.Base64

plugins {
    id("org.gradle.maven-publish")
}

publishing {
    repositories.maven("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/") {
        name = "OSSRH"

        credentials {
            username = System.getenv("OSSRH_USER")
            password = System.getenv("OSSRH_KEY")
        }
    }

    publications.withType<MavenPublication> {
        // Provide artifacts information requited by Maven Central
        pom {
            name.set("MOKO resources")
            description.set("Resources access for Kotlin Multiplatform development (mobile first)")
            url.set("https://github.com/rustamsmax/moko-resources")
            licenses {
                license {
                    name.set("Apache-2.0")
                    distribution.set("repo")
                    url.set("https://github.com/rustamsmax/moko-resources/blob/master/LICENSE.md")
                }
            }

            developers {
                developer {
                    id.set("rsktash")
                    name.set("Rustam Samandarov")
                    email.set("rustamsmax@gmail.com")
                }
            }

            scm {
                connection.set("scm:git:ssh://github.com/rustamsmax/moko-resources.git")
                developerConnection.set("scm:git:ssh://github.com/rustamsmax/moko-resources.git")
                url.set("https://github.com/rustamsmax/moko-resources")
            }
        }
    }
}

val signingKeyId: String? = System.getenv("SIGNING_KEY_ID")
if (signingKeyId != null) {
    apply(plugin = "signing")

    configure<SigningExtension> {
        val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
        val signingKey: String? = System.getenv("SIGNING_KEY")?.let { base64Key ->
            String(Base64.getDecoder().decode(base64Key))
        }

        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(publishing.publications)
    }

    val signingTasks = tasks.withType<Sign>()
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(signingTasks)
    }
}
