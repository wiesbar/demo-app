package example.catalog

import org.testcontainers.elasticsearch.ElasticsearchContainer

internal object ElasticsearchTestContainer {
    private const val IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.0.3"

    val instance: ElasticsearchContainer =
        ElasticsearchContainer(IMAGE)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .also { it.start() }
}
