package com.touchkiss.utils.beanutils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Author Touchkiss
 * @description: bean工具类
 * @create: 2019-04-22 08:45
 */
public class BeanUtil {
    public static Map<String, Item> relationMap = new HashMap<>();

    /**
     * @Description: 复制对象属性到新对象
     * @Param: from源对象、to目标对象
     * @return:
     * @Author: Touchkiss
     * @Date: 2019/4/22
     */
    public static void beanCopy(Object from, Object to) {
        try {
            if (to == null) {
                System.out.println("请先初始化目标对象");
                return;
            }
            Class<?> fromClazz = from.getClass();
            Class<?> toClazz = to.getClass();
            String twoClazzName = toClazz.getName();
            String oneClazzName = fromClazz.getName();
            String combineName = oneClazzName + "," + twoClazzName;
            if (!relationMap.containsKey(combineName)) {
                Item item = new Item(fromClazz, toClazz);
                relationMap.put(item.getClassNameCombine(), item);
                if (!twoClazzName.equals(oneClazzName)) {
                    Item reverse = new Item(item);
                    relationMap.put(reverse.getClassNameCombine(), reverse);
                }
            }
            if (relationMap.containsKey(combineName)) {
                Item item = relationMap.get(combineName);
                if (item != null && item.getFieldRelations() != null) {
                    Set<String> fieldRelations = item.getFieldRelations();
                    if (fieldRelations.size() > 0) {
                        for (String fieldName : fieldRelations) {
                            try {
                                Field fromDeclaredField = fromClazz.getDeclaredField(fieldName);
                                fromDeclaredField.setAccessible(true);
                                Field declaredField = toClazz.getDeclaredField(fieldName);
                                declaredField.setAccessible(true);
                                declaredField.set(to, fromDeclaredField.get(from));
                            } catch (NoSuchFieldException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
//            e.printStackTrace();
        }
    }

    public static class Item {
        private String classNameCombine;
        private String classNameOne;
        private String classNameTwo;
        private Set<String> fieldRelations;

        public Item(Class clazzOne, Class clazzTwo) {
            classNameOne = clazzOne.getName();
            classNameTwo = clazzTwo.getName();
            classNameCombine = classNameOne + "," + classNameTwo;
            fieldRelations = new HashSet<>();
            for (Field field : clazzOne.getDeclaredFields()) {
                try {
                    String fieldName = field.getName();
                    Class<?> fieldType = field.getType();
                    Field declaredField = clazzTwo.getDeclaredField(fieldName);
                    if (declaredField != null && fieldType.equals(declaredField.getType())) {
                        fieldRelations.add(fieldName);
                    }
                } catch (NoSuchFieldException e) {
//                    e.printStackTrace();
                }
            }
        }

        public Item(Item item) {
            this.classNameOne = item.classNameTwo;
            this.classNameTwo = item.classNameOne;
            this.classNameCombine = this.classNameOne + "," + this.classNameTwo;
            this.fieldRelations = item.fieldRelations;
        }

        public String getClassNameCombine() {
            return classNameCombine;
        }

        public Set<String> getFieldRelations() {
            return fieldRelations;
        }
    }
}
