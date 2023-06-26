package ls.ipld.config;

import java.io.File;
import java.io.IOException;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import ls.ipld.data.JobCompletionNotificationListener;
import ls.ipld.data.MatchDataProcessor;
import ls.ipld.data.MatchInput;
import ls.ipld.model.Match;

@Configuration
@EnableBatchProcessing
@EnableConfigurationProperties
@ConfigurationProperties
public class BatchConfig {

    private final String[] FIELD_NAMES = new String[] { "id", "city", "date", "player_of_match", "venue",
            "neutral_venue", "team1", "team2", "toss_winner", "toss_decision", "winner", "result", "result_margin",
            "eliminator", "method", "umpire1", "umpire2" };

  
	@Autowired
    public JobBuilderFactory jobBuilderFactory;

    @Autowired
    public StepBuilderFactory stepBuilderFactory;
    
    @Value("${geodata.file}")
    private String geodataFilePath;
//
//    @Value("${geodata.file}")
//    private String filePath; 

@Autowired
private ResourceLoader resourceLoader; 

    @Bean
    public FlatFileItemReader<MatchInput> reader() throws IOException {
        FlatFileItemReader<MatchInput> reader = new FlatFileItemReader<>();
        
       // FileSystemResource resource = new FileSystemResource("/IPL-Dashbaord/src/main/resources/match-data.csv");

Resource resource = resourceLoader.getResource(geodataFilePath);
        File file = resource.getFile();
        reader.setResource(resource);
        
        // Configure the reader
        reader.setLinesToSkip(1);  // Skip the header line
        reader.setLineMapper(new DefaultLineMapper<MatchInput>() {
            {
                setLineTokenizer(new DelimitedLineTokenizer() {
                    {
                        setNames(FIELD_NAMES);
                    }
                });
                setFieldSetMapper(new BeanWrapperFieldSetMapper<MatchInput>() {
                    {
                        setTargetType(MatchInput.class);
                    }
                });
            }
        });
        
        return reader;
    }


    @Bean
    public MatchDataProcessor processor() {
        return new MatchDataProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<Match> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Match>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO match (id, city, date, player_of_match, venue, team1, team2, toss_winner, toss_decision, match_winner, result, result_margin, umpire1, umpire2) "
                        + " VALUES (:id, :city, :date, :playerOfMatch, :venue, :team1, :team2, :tossWinner, :tossDecision, :matchWinner, :result, :resultMargin, :umpire1, :umpire2)")
                .dataSource(dataSource).build();
    }

    @Bean
    public Job importUserJob(JobCompletionNotificationListener listener, Step step1) {
        return jobBuilderFactory
            .get("importUserJob")
            .incrementer(new RunIdIncrementer())
            .listener(listener)
            .flow(step1)
            .end()
            .build();
    }

    @Bean
    public Step step1(JdbcBatchItemWriter<Match> writer) throws IOException {
        return stepBuilderFactory
            .get("step1")
            .<MatchInput, Match>chunk(10)
            .reader(reader())
            .processor(processor())
            .writer(writer)
            .build();
    }
}