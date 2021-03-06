/*
 * Copyright (c) 2012, Nikita Lipsky, Excelsior LLC.
 *
 *  The Nothing System is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  The Nothing System is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 *
*/

package com.excelsior.nothing;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author kit
 * Date: 05.10.12
 */
public class MethodHandle {

    private static ArrayList<URL> baseURLs = new ArrayList<URL>();

    Object thisRef;
    HashMap<Integer, ArrayList<Method>> methods = new HashMap<Integer, ArrayList<Method>>();

    private static class OneClassClassloder extends ClassLoader {

        private String className;
        private boolean loaded;
        private Class clazz;

        private OneClassClassloder(String className) {
            this.className = className;
        }

        public Class findClass (String name) throws ClassNotFoundException {
            if (!name.replace('.', '/').equals(className)) {
                throw new ClassNotFoundException ();
            }
            if (loaded) return clazz;
            try {
                FileInputStream fis = new FileInputStream (className + ".class");
                return tryToLoadClass(fis); //try to load class locally
            } catch (IOException e) {
            }
            for (URL url: baseURLs) {
                try {
                    //look for class in available base URLs
                    ///TODO: map baseURL to document we dowloaded earlier and use context of it here
                    return tryToLoadClass(new URL(url.getProtocol(), url.getHost(), className + ".class").openStream());
                } catch (Exception e) {
                }
            }
            throw new ClassNotFoundException ();
        }

        private Class tryToLoadClass(InputStream in) throws IOException {
            byte buf[] = new byte [in.available()];
            in.read(buf);
            in.close();
            clazz =  defineClass (buf, 0, buf.length);
            loaded = true;
            return clazz;
        }
    }

    private static HashMap<String, OneClassClassloder> classes = new HashMap<String, OneClassClassloder>();

    public static void addBaseURL(URL url) {
        baseURLs.add(url);
    }

    public static void addClass(String className) {
        classes.put(className, new OneClassClassloder(className));
    }

    private static class SplittedString {
        public String start;
        public String lastPart;

        private SplittedString(String start, String lastPart) {
            this.start = start;
            this.lastPart = lastPart;
        }
    }

    private static SplittedString splitString(String s) {
        int lastDot = s.lastIndexOf('.');
        if (lastDot == -1) return null;
        return new SplittedString(s.substring(0, lastDot), s.substring(lastDot + 1));
    }

    private static final String[] importPacks = new String[]{"", "java.lang", "com.excelsior.nothing"};

    private boolean exists(String clazz) {
        if (new File(clazz).exists()) return true;
        for (URL url: baseURLs) {
            try {
                new URL(url.getProtocol(), url.getHost(), clazz).openConnection();
                return true;
            } catch (IOException e) {
                continue;
            }
        }
        return false;
    }

    private Class lookForClass(String clazz) throws ClassNotFoundException {
        for (String imp: importPacks) {
            try {
                return Class.forName(imp.isEmpty()?clazz:imp + "." + clazz);
            } catch (ClassNotFoundException e) {
                continue;
            }
        }

        if(!classes.containsKey(clazz) && exists(clazz + ".class")) {
            addClass(clazz);
        }

        ClassLoader cl = classes.get(clazz);
        if (cl != null) {
            return Class.forName(clazz, true, cl);
        }
        throw new ClassNotFoundException(clazz);
    }

    private Class findClass(String classRef) {
        if (classRef.startsWith("@")) {
            Object o = Kernel.getFromRegistry(classRef.substring(1));
            if (o == null) {
                return null;
            }
            thisRef = o;
            return o.getClass();
        }
        try {
            return lookForClass(classRef);
        } catch (ClassNotFoundException e) {
            SplittedString ss = splitString(classRef);
            if (ss == null) {
                return null;
            }
            try {
                Class c = lookForClass(ss.start);
                Field f = c.getField(ss.lastPart);
                f.setAccessible(true);
                thisRef = f.get(null);
                if (thisRef == null) return null;
                return thisRef.getClass();
            } catch (ClassNotFoundException e1) {
                return null;
            } catch (NoSuchFieldException e1) {
                return null;
            } catch (IllegalAccessException e1) {
                return null;
            }
        }
    }

    public MethodHandle(String cmd){
        SplittedString ss = splitString(cmd);
        if (ss == null) throw new IllegalArgumentException();

        Class c = findClass(ss.start);
        if (c == null) throw new IllegalArgumentException();
        while (c!=null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(ss.lastPart)) {
                    Integer key = m.getParameterTypes().length;
                    ArrayList<Method> value = methods.get(key);
                    if (value == null) {
                        value = new ArrayList<Method>();
                        methods.put(key, value);
                    }
                    value.add(m);
                }
            }
            c = c.getSuperclass();
        }
        if (methods.isEmpty())
            throw new IllegalArgumentException();
    }

    private ArrayList<Method> findMethodByArgsLength(int numOfArgs) {
        for (int i = numOfArgs; i>=0; i--) {
            ArrayList<Method> candidate = methods.get(i);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private boolean canBeConvertedFromString(Class c) {
        return true;//
        // (c == String.class) || (c == int.class) || (c == long.class);
    }

    private Object convertFromString(String arg, Class c) {
        if (arg.startsWith("&") && (c == String.class)) {
            return arg.substring(1);
        } else if (arg.startsWith("@") && !c.isPrimitive()) {
            if (arg.indexOf('.') > 0) {
                return Kernel.executeCommand(arg);
            } else {
                Object o = Kernel.getFromRegistry(arg.substring(1));
                if (o == null) return null;
                if (c.isAssignableFrom(o.getClass())) return o;
                throw new IllegalArgumentException("Wrong param type: " + c);
            }
        } else if (c == String.class) {
            return arg;
        } else if (c == int.class) {
            return Integer.valueOf(arg);
        } else if (c == long.class) {
            return Long.valueOf(arg);
        } else {
            throw new IllegalArgumentException("Wrong param type: " + c);
        }
    }

    public Object invoke(String[] args) {
        try {
            ArrayList<Method> mths = findMethodByArgsLength(args.length);
            if (mths == null) {
                throw new IllegalArgumentException("Can not find method for specified arguments");
            }
            outlabel:
            for (Method m: mths) {
                Class[] paramTypes = m.getParameterTypes();
                Object methArgs[] = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    Class parT = paramTypes[i];
                    if (canBeConvertedFromString(parT)) {
                        try {
                            methArgs[i] = convertFromString(args[i], parT);
                        } catch (Exception e) {
                            continue outlabel;
                        }
                    } else {
                        continue outlabel;
                    }
                }
                m.setAccessible(true);
                return m.invoke(thisRef, methArgs);
            }
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle getMethodHandle(String cmd) {
        try {
            return new MethodHandle(cmd);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
