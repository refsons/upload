package com.hazelcast.samples.eureka.partition.groups;

import com.hazelcast.config.*;
import com.hazelcast.spi.discovery.integration.DiscoveryServiceProvider;
import com.hazelcast.spi.merge.LatestUpdateMergePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

import static com.hazelcast.config.PartitionGroupConfig.MemberGroupType.ZONE_AWARE;

/**
 * Creates a Hazelcast configuration object, which Spring Boot
 * will use to create a Hazelcast instance based on that configuration.
 */
@Configuration
public class MyConfiguration {

    @Value("quorum.name:AT_LEAST_TWO_NODES")
    private String quorumName;

    @Value("partition.group.iplist:1.2.3.4,5.4.3.3.2")
    private List<String> partitionList;

    @Value("partition.group.type:CUSTOM")
    private String memberGroupType;

    @Value("split.brain.merge.policy:hz.ADD_NEW_ENTRY")
    private String mergePolicy;

    @Value("pnl.map.config.backupCount:1")
    private int backupCount;

    @Value("network.ssl.keystore.path:keystore.jks")
    private String keystorePath;

    @Value("network.ssl.keystore.password")
    private String keystorePassword;

    @Value("network.ssl.encryption.algo")
    private String encryptionAlgo;

    @Value("network.ssl.encryption.protocol")
    private String sslProtocol;

    @Value("network.ssl.factoryClass:com.hazelcast.nio.ssl.BasicSSLContextFactory")
    private String sslConfigFactoryClass;

    @Value("network.symetricEncryption.password")
    private String symmetricEncryptionPassword;

    @Value("network.symetricEncryption.salt")
    private String symmetricEncryptionSalt;

    @Value("network.symetricEncryption.algo:PBEWithMD5AndDES")
    private String symmetricEncryptionAlgo;

    /**
     * Create a Hazelcast configuration object for a server that differs from
     * the default in four ways.
     * <ol>
     * <li><b>Name</b>
     * Use {@link Constants} to give the cluster a name,
     * here more for logging and diagnostics than to avoid inadvertant
     * connections.
     * </li>
     * <li><b>Networking</b>
     * Turn off the default multicast broadcasting mechanism for servers
     * to find each other, in favor of a plug-in that will somehow provide
     * the locations of the servers. The "<em>somehow</em>" being to find
     * their locations in {@code Eureka}.
     * </li>
     * <li><b>Partition Groups</b>
     * Activate {@code ZONE_AWARE} partitioning, where we guide Hazelcast
     * in where to place data master and data backup copies with external
     * meta-data (which we get from {@code Eureka}.
     * </li>
     * <li><b>Map Config</b>
     * Configure maps for safety (ie. have backups) or where safety isn't
     * required (ie. have no backups). Backups get placed in a different zone
     * from the original.
     * </ol>
     *
     * @param discoveryServiceProvider A {@link MyDiscoveryServiceProvider} instance.
     * @return Configuration for a Hazelcast server.
     */
    @Bean
    public Config config(DiscoveryServiceProvider discoveryServiceProvider) {



        Config config = new Config();

        // Discovery and Cluster Join Strategy
        config.setProperty("hazelcast.discovery.enabled", Boolean.TRUE.toString());
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();
        joinConfig
                .getMulticastConfig()
                .setEnabled(false);

        joinConfig
                .getDiscoveryConfig()
                .setDiscoveryServiceProvider(discoveryServiceProvider);





        // Group Naming
        config.getGroupConfig().setName(Constants.CLUSTER_NAME);

        PartitionGroupConfig partitionGroupConfig = config.getPartitionGroupConfig();
        partitionGroupConfig.setGroupType(PartitionGroupConfig.MemberGroupType.valueOf(memberGroupType));
        partitionGroupConfig.setEnabled(true);

        //Do not allow main and backup instances on the same host or DataCenter
        //Partitions
        partitionList.stream().map(ips ->
                partitionGroupConfig.addMemberGroupConfig(new MemberGroupConfig()
                        .setInterfaces(Arrays.asList(ips.split(",")))));



        //Setup Quorum And Split Brain Merge policy
        QuorumConfig quorumConfig = new QuorumConfig();
        quorumConfig.setName(quorumName).setEnabled(true).setSize(2);
        config.addQuorumConfig(quorumConfig);
        config.getMapConfigs().put("pnl", new MapConfig()
                .setEvictionPolicy(EvictionPolicy.NONE)
                //3 is set as the backup-count, then all entries of
                //the map will be copied to another JVM for fail-safety. 0 means no backup.

                .setBackupCount(backupCount)
                //Once Split Brain has identified a smaller cluster is merging back in, define the policy of hte mewrge here.

                .setMergePolicyConfig(new MergePolicyConfig().setPolicy(mergePolicy))
                .setName(quorumName)
                .setQuorumName(quorumName)
        );


        //Encrypt entire socket level communication among all Hazelcast members.
        //Encryption is based on Java Cryptography Architecture In symmetric encryption, each node uses the same key, so the key is shared.
        SymmetricEncryptionConfig encryption = config.getNetworkConfig().getSymmetricEncryptionConfig();
        encryption.setAlgorithm(symmetricEncryptionAlgo);
        encryption.setEnabled(true);
        //private key password
        encryption.setKey(symmetricEncryptionPassword.getBytes());
        //salt value to use when generating the secret key
        encryption.setSalt(symmetricEncryptionSalt);
         //pass phrase to use when generating the secret key
        encryption.setPassword(symmetricEncryptionPassword);


        //SSL socket communication among all Hazelcast members
        config.getNetworkConfig()
                .getSSLConfig()
                .setEnabled(true)
                .setFactoryClassName(sslConfigFactoryClass)
                .setProperty("keystore", keystorePath)
                .setProperty("keystorePassword",keystorePassword)
                .setProperty("keyManagerAlgorithm",encryptionAlgo)
                .setProperty("trustManagerAlgorithm",encryptionAlgo)
                .setProperty("protocol",sslProtocol);


        return config;
    }
}
