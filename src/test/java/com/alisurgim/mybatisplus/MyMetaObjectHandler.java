package com.alisurgim.mybatisplus;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;


@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdTime", Date.class, new Date());
        this.strictInsertFill(metaObject, "createdBy", String.class, "admin");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedTime", Date.class, new Date());
        this.strictUpdateFill(metaObject, "updatedBy", String.class, "admin");
    }
}