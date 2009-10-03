/*
 * Copyright (c) 2009 Mysema Ltd.
 * All rights reserved.
 * 
 */
package com.mysema.query.apt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;

import net.jcip.annotations.Immutable;

import org.apache.commons.lang.StringUtils;

import com.mysema.query.annotations.QueryType;
import com.mysema.query.codegen.ClassModel;
import com.mysema.query.codegen.ConstructorModel;
import com.mysema.query.codegen.FieldModel;
import com.mysema.query.codegen.ParameterModel;
import com.mysema.query.codegen.TypeCategory;
import com.mysema.query.codegen.TypeModel;

/**
 * @author tiwe
 *
 */
@Immutable
public final class EntityElementVisitor extends SimpleElementVisitor6<ClassModel, Void>{
    
    private final ProcessingEnvironment env;
    
    private final String namePrefix;
    
    private final APTModelFactory typeFactory;
    
    private final Configuration configuration;
    
    EntityElementVisitor(ProcessingEnvironment env, Configuration conf, String namePrefix, APTModelFactory typeFactory){
        this.env = env;
        this.configuration = conf;
        this.namePrefix = namePrefix;
        this.typeFactory = typeFactory;
    }
    
    @Override
    public ClassModel visitType(TypeElement e, Void p) {
        Elements elementUtils = env.getElementUtils();
        TypeModel sc = typeFactory.create(e.getSuperclass(), elementUtils);
        TypeModel c = typeFactory.create(e.asType(), elementUtils);
        ClassModel classModel = new ClassModel(namePrefix, sc.getName(), c.getPackageName(), c.getName(), c.getSimpleName());
        List<? extends Element> elements = e.getEnclosedElements();
    
        // CONSTRUCTORS
        
        for (ExecutableElement constructor : ElementFilter.constructorsIn(elements)){
            if (configuration.isValidConstructor(constructor)){
                List<ParameterModel> parameters = new ArrayList<ParameterModel>(constructor.getParameters().size());
                for (VariableElement var : constructor.getParameters()){
                    TypeModel varType = typeFactory.create(var.asType(), elementUtils);
                    parameters.add(new ParameterModel(var.getSimpleName().toString(), varType.getName()));
                }
                classModel.addConstructor(new ConstructorModel(parameters));    
            }                
        }  

        VisitorConfig config = configuration.getConfig(e, elements);
                
        Set<String> blockedFields = new HashSet<String>();
        Map<String,FieldModel> fields = new HashMap<String,FieldModel>();
        Map<String,TypeCategory> types = new HashMap<String,TypeCategory>();
        
        // FIELDS
        
        if (config.isVisitFields()){
            for (VariableElement field : ElementFilter.fieldsIn(elements)){
                String name = field.getSimpleName().toString();
                if (configuration.isValidField(field)){
                    try{                        
                        TypeModel typeModel = typeFactory.create(field.asType(), elementUtils);                            
                        if (field.getAnnotation(QueryType.class) != null){
                            TypeCategory typeCategory = TypeCategory.get(field.getAnnotation(QueryType.class).value());
                            if (typeCategory == null){
                                blockedFields.add(name);
                                continue;
                            }
                            typeModel = typeModel.as(typeCategory);
                            types.put(name, typeCategory);
                        }                        
                        fields.put(name, new FieldModel(classModel, name, typeModel, null));    
                    }catch(IllegalArgumentException ex){
                        StringBuilder builder = new StringBuilder();
                        builder.append("Caught exception for field ");
                        builder.append(c.getName()).append("#").append(field.getSimpleName());
                        throw new RuntimeException(builder.toString(), ex);
                    }
                        
                }else{
                    blockedFields.add(name);
                }
            }    
        }
        
        // METHODS
        
        if (config.isVisitMethods()){
            for (ExecutableElement method : ElementFilter.methodsIn(elements)){
                String name = method.getSimpleName().toString();
                if (name.startsWith("get") && method.getParameters().isEmpty()){
                    name = StringUtils.uncapitalize(name.substring(3));
                }else if (name.startsWith("is") && method.getParameters().isEmpty()){
                    name = StringUtils.uncapitalize(name.substring(2));
                }else{
                    continue;
                }
                if (configuration.isValidGetter(method)){
                    try{
                        TypeModel typeModel = typeFactory.create(method.getReturnType(), elementUtils);
                        if (method.getAnnotation(QueryType.class) != null){
                            TypeCategory typeCategory = TypeCategory.get(method.getAnnotation(QueryType.class).value());
                            if (typeCategory == null){
                                blockedFields.add(name);
                                continue;
                            }else if (blockedFields.contains(name)){
                                continue;
                            }
                            typeModel = typeModel.as(typeCategory);
                        }else if (types.containsKey(name)){
                            typeModel = typeModel.as(types.get(name));
                        }
                        fields.put(name, new FieldModel(classModel, name, typeModel, null));    
                        
                    }catch(IllegalArgumentException ex){
                        StringBuilder builder = new StringBuilder();
                        builder.append("Caught exception for method ");
                        builder.append(c.getName()).append("#").append(method.getSimpleName());
                        throw new RuntimeException(builder.toString(), ex);
                    }
                }else{
                    blockedFields.add(name);
                }
            }   
        }
               
        
        for (Map.Entry<String,FieldModel> entry : fields.entrySet()){
            if (!blockedFields.contains(entry.getKey())){
                classModel.addField(entry.getValue());
            }
        }        
        
        return classModel;
    }
    
}
