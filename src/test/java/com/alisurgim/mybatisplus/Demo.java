package com.alisurgim.mybatisplus;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import javax.annotation.Resource;

/**
 * @author alisurgim
 * @date 2022/12/14 17:18
 */
class Demo {

    @Resource
    private UserService userService;

    /**
     * 执行以下代码时，默认将不会填充被
     * <code>@TableField(fill = FieldFill.UPDATE)</code>标记的字段。
     * 插件解决了这个问题
     *
     * @throws Exception
     */
    public void testUpdate() throws Exception {
        userService.update(Wrappers.lambdaUpdate(User.class)
                .set(User::getName, "123").eq(User::getId, 1));
    }
}
