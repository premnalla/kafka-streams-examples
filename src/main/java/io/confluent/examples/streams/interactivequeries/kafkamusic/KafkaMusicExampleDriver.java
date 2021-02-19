/*
 * Copyright Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.examples.streams.interactivequeries.kafkamusic;

import io.confluent.examples.streams.avro.PlayEvent;
import io.confluent.examples.streams.avro.Song;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serdes;

// Prem (begin)
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
//Prem (end)

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;

//Prem (begin)
import java.util.HashMap;
//Prem (end)

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.IOException;



/**
 * This is a sample driver for the {@link KafkaMusicExample}.
 * To run this driver please first refer to the instructions in {@link KafkaMusicExample}.
 * You can then run this class directly in your IDE or via the command line.
 *
 * To run via the command line you might want to package as a fatjar first. Please refer to:
 * <a href='https://github.com/confluentinc/kafka-streams-examples#packaging-and-running'>Packaging</a>
 *
 * Once packaged you can then run:
 * <pre>
 * {@code
 * $ java -cp target/kafka-streams-examples-6.0.1-standalone.jar \
 *      io.confluent.examples.streams.interactivequeries.kafkamusic.KafkaMusicExampleDriver
 * }
 * </pre>
 * You should terminate with Ctrl-C
 */
public class KafkaMusicExampleDriver {

  // Prem (begin)
  final static String kafkaBootStrap = System.getenv("KAFKA_BOOTSTRAP");
  final static String schemaRegUrl = System.getenv("SCHEMA_REGISTRY_URL");
  final static String basicAuthCredSource = System.getenv("BASIC_AUTH_CRED_SOURCE");
  final static String basicAuthUserInfo = System.getenv("BASIC_AUTH_USER_INFO");
  final static String sslTrustStoreLocation = System.getenv("SSL_TRUST_STORE_LOC");
  final static String sslKeyStoreLocation = System.getenv("SSL_KEY_STORE_LOC");
  final static String sslPw = System.getenv("SSL_COMMON_PASSWORD");
  // Prem (end)

  public static void main(final String [] args) throws Exception {
	// Prem (begin)
    final String bootstrapServers = args.length > 0 ? args[0] : kafkaBootStrap;
    final String schemaRegistryUrl = args.length > 1 ? args[1] : schemaRegUrl;
    // Prem (end)
    
    System.out.println("Connecting to Kafka cluster via bootstrap servers " + bootstrapServers);
    System.out.println("Connecting to Confluent schema registry at " + schemaRegistryUrl);

    // Read comma-delimited file of songs into Array
    final List<Song> songs = new ArrayList<>();
    final String SONGFILENAME= "song_source.csv";
    final InputStream inputStream = KafkaMusicExample.class.getClassLoader().getResourceAsStream(SONGFILENAME);
    final InputStreamReader streamReader = new InputStreamReader(inputStream, UTF_8);
    try (final BufferedReader br = new BufferedReader(streamReader)) {
      String line = null;
      while ((line = br.readLine()) != null) {
        final String[] values = line.split(",");
        final Song newSong = new Song(Long.parseLong(values[0]), values[1], values[2], values[3], values[4]);
        songs.add(newSong);
      }
    }

    final Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    // Prem (begin)
    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
    props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslTrustStoreLocation);
    props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslPw);
    props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12");
    props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeyStoreLocation);
    props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslPw);
    props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, sslPw);
    // Prem (end)

    // Prem (begin)
    final Map<String, String> serdeConfig = new HashMap<>();
    serdeConfig.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    serdeConfig.put(AbstractKafkaSchemaSerDeConfig.BASIC_AUTH_CREDENTIALS_SOURCE, basicAuthCredSource);
    serdeConfig.put(AbstractKafkaSchemaSerDeConfig.USER_INFO_CONFIG, basicAuthUserInfo);
    // Prem (end)

    final SpecificAvroSerializer<PlayEvent> playEventSerializer = new SpecificAvroSerializer<>();
    playEventSerializer.configure(serdeConfig, false);
    final SpecificAvroSerializer<Song> songSerializer = new SpecificAvroSerializer<>();
    songSerializer.configure(serdeConfig, false);

    final KafkaProducer<String, PlayEvent> playEventProducer = new KafkaProducer<>(props,
                                                                                   Serdes.String().serializer(),
                                                                                   playEventSerializer);

    final KafkaProducer<Long, Song> songProducer = new KafkaProducer<>(props,
                                                                       new LongSerializer(),
                                                                       songSerializer);

    songs.forEach(song -> {
      System.out.println("Writing song information for '" + song.getName() + "' to input topic " +
          KafkaMusicExample.SONG_FEED);
      songProducer.send(new ProducerRecord<>(KafkaMusicExample.SONG_FEED, song.getId(), song));
    });

    songProducer.close();
    final long duration = 60 * 1000L;
    final Random random = new Random();

    // send a play event every 100 milliseconds
    while (true) {
      final Song song = songs.get(random.nextInt(songs.size()));
      System.out.println("Writing play event for song " + song.getName() + " to input topic " +
          KafkaMusicExample.PLAY_EVENTS);
      playEventProducer.send(
          new ProducerRecord<>(KafkaMusicExample.PLAY_EVENTS,
                                                "uk", new PlayEvent(song.getId(), duration)));
      Thread.sleep(100L);
    }
  }

}
