## Mybatisplus辅助自动填充参数插件

### 背景
Mybatisplus的MetaObjectHandler用于为实体对象填充字段。
```java
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
```
以上两个方法，分别表示在insert和update填充字段。但是在某些情况下会不起作用，比如：
```java
    public void testUpdate() {
        userService.update(Wrappers.lambdaUpdate(User.class)
                .set(User::getName, "123").eq(User::getId, 1));
    }
```
在此情况下，因为没有传递实体对象，使用Wrapper作为唯一入参，依赖于实体对象的MetaObjectHandler将不起作用。

### 实现原理
本插件支持在在以上场景下，支持更新时自动填充。原理是拦截SQL请求，检测入参若没有实体对象，将手动创建一个，同时调用MetaObjectHandler的updateFill方法用于接收用户配置的填充数据。

用户有可能在Wrapper中指定了本该自动填充的字段，所以会将Wrapper与Entity进行比对，对这些字段忽略，仅填充Wrapper中未指定的字段。

最终将Entity和Wrapper一起作为参数向下传递。

以下为Mybatisplus的更新语句SQL格式，是同时支持et（Entity）和ew（Wrapper）的，所以增加Entity作为新的参数来实现此功能。
```sql
<script>
    UPDATE abc <set>
    <if test="et != null">
    <if test="et['id'] != null">id=#{et.id},</if>
    <if test="et['updatedTime'] != null">bane=#{et.updatedTime}</if>
    <if test="et['name'] != null">bane=#{et.name}</if>
    <if test="ew != null and ew.sqlSet != null">${ew.sqlSet}</if>
    </set> 
    <where>
        <choose>
            <when test="ew != null">
                <if test="ew.entity != null">
                    <if test="ew.entity.id != null">attr_id=#{ew.entity.id}</if>
                </if>
                AND is_delete=0
                <if test="ew.sqlSegment != null and ew.sqlSegment != '' and ew.nonEmptyOfNormal">
                    AND ${ew.sqlSegment}
                </if>
                <if test="ew.sqlSegment != null and ew.sqlSegment != '' and ew.emptyOfNormal">
                    ${ew.sqlSegment}
                </if>
            </when>
            <otherwise>
            </otherwise>
        </choose>
    </where> 
</script>



```





