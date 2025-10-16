package com.revenera.gcs;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.bind.DatatypeConverter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.flexera.schneider.fnesigner.LicenseFactory;
import com.flexera.schneider.fnesigner.LicenseResult;
import com.flexera.schneider.fnesigner.Nova;

@SpringBootApplication
@EnableScheduling
public class Application {

  Optional<Nova> nova = Optional.empty();

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @PreDestroy
  public void preConstruct() {
    System.out.println("@PreDestroy");
    System.out.println("-----------");
  }

  @PostConstruct
  public void postConstruct() {
    System.out.println("@PostConstruct");
    System.out.println("--------------");
  }

  @Bean
  public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
    return args -> {

//			System.out.println("Let's inspect the beans provided by Spring Boot:");
//
//			String[] beanNames = ctx.getBeanDefinitionNames();
//			Arrays.sort(beanNames);
//			for (String beanName : beanNames) {
//				System.out.println(beanName);
//			}

      System.out.println("Loading Nova");
      System.out.println("------------");

      nova = Optional.of(new Nova());

      nova.ifPresent(value -> {
        try {
          value.populateIdentity();

          System.out.printf("message |  %s\n", value.getMessage());
        }
        catch (final Throwable t) {
          System.err.printf("%s | %s\n", t.getClass().getName(), t.getLocalizedMessage());
        }
      });
    };
  }

  @Scheduled(cron = "*/30 * * * * *")
  public void doHousekeeping() {
    System.out.println("@Scheduled");
    System.out.println("----------");
    System.out.printf("lib path = %s\n", System.getProperty("java.library.path"));
    /**
     * setup the license factory - this should be a persistent object and not
     * created for each entitlement
     */
    final var factory = LicenseFactory.create();

    // we need the serial number
    final var serialNumber = "12345616024L001LV800001";

    // add in the desired skus
    final List<String> skus = new ArrayList<>();
    skus.add("LV850001"); // there is no SKU LVB50001 - so this will not generate a license and will
    // return
    skus.add("LV850002");
    skus.add("LV850003");
    skus.add("LV850004");
    skus.add("LV850005");
    skus.add("LV850006"); // there is no SKU LVB50006 - so this will not generate a license and will
    // return

    try {
      /**
       * generate licenses for the 4 skus
       */
      final var t0 = System.nanoTime();

      final var results = factory.generateLicenses(serialNumber, skus);

      final var t1 = System.nanoTime();

      System.out.printf("result for %s contains %d items in %f secs\n", serialNumber, results.size(),
          (t1 - t0) / 1000000000.0);

      for (final LicenseResult result : results) {
        if (result.getStatus() == LicenseResult.Status.Valid) {
          System.out.printf("%s\n%s\n\n", result.getSku(), result.getLicense());

          try (var licenseFile = new FileOutputStream(
              String.format("license-%s-%s.bin", serialNumber, result.getSku()))) {
            licenseFile.write(DatatypeConverter.parseBase64Binary(result.getLicense()));
          }
          catch (final IOException e) {
            System.err.println("exception " + e.getMessage());
          }
        }
        else {
          System.err.printf("sku:%s... %s\n", result.getSku(), result.getStatus().toString());
        }
      }
    }
    catch (final Throwable t) {
      System.err.printf("%s | %s\n", t.getClass().getName(), t.getLocalizedMessage());
    }
  }

}
