package com.zhuma.demo.util;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.zhuma.demo.annotation.FieldAlias;

/**
 * @desc Bean操作工具类（拷贝方法时支持添加{@link FieldAlias} 注解）
 *
 * @author zhuamer
 * @since 7/6/2017 3:13 PM
 */
public class BeanUtil {

	public static void copyProperties(Object source, Object target, String... ignoreProperties) throws BeansException {

		Assert.notNull(source, "Source must not be null");
		Assert.notNull(target, "Target must not be null");

		Class<?> actualEditable = target.getClass();
		PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(actualEditable);
		List<String> ignoreList = (ignoreProperties != null ? Arrays.asList(ignoreProperties) : null);

		for (PropertyDescriptor targetPd : targetPds) {
			Method writeMethod = targetPd.getWriteMethod();
			if (writeMethod != null && (ignoreList == null || !ignoreList.contains(targetPd.getName()))) {
				PropertyDescriptor sourcePd = getPropertyDescriptor(source.getClass(), target.getClass(), targetPd);
				if (sourcePd != null) {
					Method readMethod = sourcePd.getReadMethod();
					if (readMethod != null && ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], readMethod.getReturnType())) {
						try {
							if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
								readMethod.setAccessible(true);
							}
							Object value = readMethod.invoke(source);
							if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
								writeMethod.setAccessible(true);
							}
							writeMethod.invoke(target, value);
						} catch (Throwable ex) {
							throw new FatalBeanException("Could not copy property '" + targetPd.getName() + "' from source to target", ex);
						}
					}
				}
			}
		}

	}

//	public static <T,E> List<T> toTargetList(List<E> sourceList, Class<T> targetClazz, String... ignoreProperties) {
//		if (sourceList == null || targetClazz == null) {
//			return null;
//		}
//
//		List<T> targetList = Lists.newArrayList();
//		sourceList.forEach(e -> {
//			try {
//				T t = targetClazz.newInstance();
//				BeanUtil.copyProperties(e, t, ignoreProperties);
//				targetList.add(t);
//			} catch (InstantiationException | IllegalAccessException exception) {
//				exception.printStackTrace();
//			}
//		});
//
//		return targetList;
//	}

//	public static <T> T mapToObject(Map<String, Object> map, Class<T> targetClazz) throws BeansException {
//		if (map == null || targetClazz == null) {
//			return null;
//		}
//
//		clearInvalidValue(map);
//
//		//处理时间格式
//		DateConverter dateConverter = new DateConverter();
//		//设置日期格式//TODO 扩展其他方式
//		dateConverter.setPatterns(new String[] {"yyyy-MM-dd HH:mm:ss.SSSSSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"});
//		//注册格式
//		ConvertUtils.register(dateConverter, Date.class);
//
//		Map<String, Object> fatMap = loadPropertyAndAnnotationValueFatMap(map, targetClazz);
//
//		T bean = null;
//		try {
//			bean = targetClazz.newInstance();
//			org.apache.commons.beanutils.BeanUtils.populate(bean, fatMap);
//		} catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
//			e.printStackTrace();
//		}
//
//		return bean;
//	}

//	private static void clearInvalidValue(Map<String, Object> map) {
//		String[] invalidTimeStrs = new String[]{"0000-00-00 00:00:00.000000", "0000-00-00 00:00:00", "0000-00-00"};
//
//		map.forEach((k,v)-> {
//			if (ArrayUtils.contains(invalidTimeStrs, v)) {
//				map.put(k, null);
//			}
//		});
//	}
//
//	public static <T> List<T> mapListToObjectList(List<Map<String, Object>> mapList, Class<T> targetClazz) throws BeansException {
//		if (mapList == null || targetClazz == null) {
//			return null;
//		}
//
//		List<T> beanList = Lists.newArrayList();
//		mapList.forEach(map -> {
//			beanList.add(mapToObject(map, targetClazz));
//		});
//
//		return beanList;
//	}
//
//	public static Map<?, ?> objectToMap(Object obj) {
//		if (obj == null)
//			return null;
//
//		return new org.apache.commons.beanutils.BeanMap(obj);
//	}

	private static PropertyDescriptor getPropertyDescriptor(Class<?> sourceClazz, Class<?> targetClazz, PropertyDescriptor targetPd) {
		PropertyDescriptor sourcePd = BeanUtils.getPropertyDescriptor(sourceClazz, targetPd.getName());
		if (sourcePd == null) {
			try {
				Field field = targetClazz.getDeclaredField(targetPd.getName());
				FieldAlias[] fieldAliases = field.getAnnotationsByType(FieldAlias.class);
				if (fieldAliases == null) {
					return null;
				}

				for (FieldAlias fieldAlias : fieldAliases) {
					if (ArrayUtils.isEmpty(fieldAlias.sourceClass()) || ArrayUtils.contains(fieldAlias.sourceClass(), sourceClazz)) {
						sourcePd = BeanUtils.getPropertyDescriptor(sourceClazz, fieldAlias.value());
						if (sourcePd != null) {
							return sourcePd;
						}
					}
				}
			} catch (NoSuchFieldException | SecurityException e) {
				e.printStackTrace();
			}
		}
		return sourcePd;
	}

	private static <T> Map<String, Object> loadPropertyAndAnnotationValueFatMap(Map<String, Object> map, Class<T> targetClazz) {
		Map<String, Object> fatMap = map;
		PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(targetClazz);

		for (PropertyDescriptor targetPd : targetPds) {
			Method writeMethod = targetPd.getWriteMethod();
			if (writeMethod == null) {
				continue;
			}

			try {
				Field field = targetClazz.getDeclaredField(targetPd.getName());
				FieldAlias[] fieldAliases = field.getAnnotationsByType(FieldAlias.class);
				if (fieldAliases == null) {
					continue;
				}

				for (FieldAlias fieldAlias : fieldAliases) {
					Object value = map.get(fieldAlias.value());
					if (value != null) {
						fatMap.put(targetPd.getName(), value);
					}
				}
			} catch (NoSuchFieldException | SecurityException e) {
				e.printStackTrace();
			}

		}

		return fatMap;
	}

}
