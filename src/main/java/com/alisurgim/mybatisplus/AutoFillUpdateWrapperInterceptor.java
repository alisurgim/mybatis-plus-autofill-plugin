package com.alisurgim.mybatisplus;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.Update;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.mapper.Mapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import com.baomidou.mybatisplus.extension.conditions.AbstractChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author alisurgim
 * @date 2022/12/12 15:08
 */
public class AutoFillUpdateWrapperInterceptor implements InnerInterceptor {

    private static final Map<String, Class<?>> ENTITY_CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<TableInfo, Map<Field, String>> FIELD_COLUMN_CACHE = new ConcurrentHashMap<>();

    private static final String AUTO_FILL_VALUE_KEY = "AUTO_FILL_VALUE_KEY";

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        if (SqlCommandType.UPDATE != ms.getSqlCommandType()) {
            return;
        }
        if (parameter instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) parameter;
            fillField(map, ms);
        }
    }

    /**
     * 有以下几种方式填充数据
     * 1.新创建entity，与传入的wrapper一起作为参数（缺点：entity会在未来被填充数据，但是不排除wrapper中已经set了字段，可能出现update中对相同字段有多个set）
     * 2.新创建entity，触发自动填充，将拿到的entity内被填充的字段和值放入wrapper的set中，最后丢弃entity。（需要考虑entity与wrapper中可能存在相同set字段）
     * 使用方案2，wrapper存在的字段将不被set到entity
     *
     * @param map
     * @param ms
     */
    private void fillField(Map<String, Object> map, MappedStatement ms) {
        Object et = map.getOrDefault(Constants.ENTITY, null);
        if (Objects.nonNull(et)) {
            return;
        }
        Object ew = map.getOrDefault(Constants.WRAPPER, null);
        if (ew == null || ew instanceof AbstractChainWrapper) {
            return;
        }
        Configuration configuration = ms.getConfiguration();
        Optional<MetaObjectHandler> metaObjectHandler = GlobalConfigUtils.getMetaObjectHandler(configuration);
        if (!metaObjectHandler.isPresent()) {
            return;
        }
        if (ew instanceof AbstractWrapper && ew instanceof Update) {
            Class<?> entityClazz = getEntityClass(ms.getId());
            TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClazz);
            // 无自动填充，忽略
            if (tableInfo == null || !tableInfo.isWithUpdateFill()) {
                return;
            }
            Update<?, ?> update = (Update<?, ?>) ew;
            AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) ew;

            // 缓存需要填充的field与column
            Map<Field, String> fieldStringMap = FIELD_COLUMN_CACHE.computeIfAbsent(tableInfo, this::findAutoFillField);

            // sql中存在，就不需要set了
            List<Field> needFillFields = filterNeedsFields(update, fieldStringMap);
            if (needFillFields.isEmpty()) {
                return;
            }

            // 新创建的实体对象，用于接收MetaObjectHandler传入的填充值
            MetaObject metaObject = configuration.newMetaObject(tableInfo.newInstance());
            metaObjectHandler.get().updateFill(metaObject);
            // 方式一：通过往参数map添加entity。存在风险，有可能与wrapper的字段重复
//            map.put(Constants.ENTITY, metaObject.getOriginalObject());
            // 方式二：解析entity的数据，并放入wrapper中的setSql中
            doFill(update, wrapper, fieldStringMap, needFillFields, metaObject);
        }
    }

    private List<Field> filterNeedsFields(Update<?, ?> update, Map<Field, String> fieldStringMap) {
        Set<String> existColumns = Arrays.stream(update.getSqlSet().split(Constants.COMMA))
                .map(s -> s.split(Constants.EQUALS)[0]).collect(Collectors.toSet());
        List<Field> needFillFields = new ArrayList<>();
        for (Map.Entry<Field, String> entry : fieldStringMap.entrySet()) {
            if (!existColumns.contains(entry.getValue())) {
                needFillFields.add(entry.getKey());
            }
        }
        return needFillFields;
    }

    private void doFill(Update<?, ?> update, AbstractWrapper<?, ?, ?> wrapper, Map<Field, String> fieldStringMap, List<Field> needFillFields, MetaObject metaObject) {
        int incr = 0;
        for (Field field : needFillFields) {
            String columnName = fieldStringMap.get(field);
            // 创建新key，从metaObject拿到字段数据，放入map
            String paramKey = AUTO_FILL_VALUE_KEY + incr++;
            Object paramValue = metaObject.getValue(field.getName());
            if (paramValue == null) {
                continue;
            }
            wrapper.getParamNameValuePairs().put(paramKey, paramValue);
            // 构建set key=value的sql，加入wrapper
            String setSql = columnName + Constants.EQUALS + SqlScriptUtils.safeParam(wrapper.getParamAlias() + Constants.WRAPPER_PARAM_MIDDLE + paramKey);
            update.setSql(setSql);
        }
    }


    private Map<Field, String> findAutoFillField(TableInfo tableInfo) {
        return tableInfo.getFieldList().stream().filter(TableFieldInfo::isWithUpdateFill)
                .collect(Collectors.toMap(TableFieldInfo::getField, TableFieldInfo::getColumn, (v1, v2) -> v2));
    }

    private Class<?> getEntityClass(String msId) {
        Class<?> entityClass = ENTITY_CLASS_CACHE.get(msId);
        if (null == entityClass) {
            try {
                final String className = msId.substring(0, msId.lastIndexOf('.'));
                entityClass = ReflectionKit.getSuperClassGenericType(Class.forName(className), Mapper.class, 0);
                ENTITY_CLASS_CACHE.put(msId, entityClass);
            } catch (ClassNotFoundException e) {
                throw ExceptionUtils.mpe(e);
            }
        }
        return entityClass;
    }
}