package top.tradesystem;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("top.tradesystem.krx.repository")
public class TradeSystemApplication {
    public static void main(String[] args) {
        // .env 파일 로드하여 시스템 환경 변수로 등록
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        } catch (Exception e) {
            // .env 파일이 없더라도 시스템 환경 변수가 있으면 동작하도록 예외 처리
        }
        
        SpringApplication.run(TradeSystemApplication.class, args);
    }
}
