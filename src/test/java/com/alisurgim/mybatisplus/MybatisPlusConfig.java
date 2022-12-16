package com.alisurgim.mybatisplus;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author alisurgim
 * @date 2022/12/14 17:22
 */
@Configuration
@MapperScan("com.alisurgim.mybatisplus.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new AutoFillUpdateWrapperInterceptor());
        return interceptor;
    }
}
