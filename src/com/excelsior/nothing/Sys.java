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

import javax.swing.*;
import javax.swing.text.Document;
import java.io.*;

/**
 * @author kit
 * Date: 05.10.12
 */
public class Sys {

    public static void newText() {
        Main.system.createText();
    }

    public static void newText(String title) {
        Main.TextWindow w = Main.system.createText();
        w.setTitle(title);
    }

    public static void writeText(String file, String text) throws IOException {
        OutputStreamWriter os = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(file)));
        os.write(text);
        os.close();
    }

    public static void save(String file) throws IOException {
        JTextPane cur = Main.getCurEditor();
        if (cur == null) return;
        writeText(file, cur.getText());

        Main.curTextWindow.setTitle(file);
        System.out.println("File " + file + " saved");
    }

    public static void save() throws IOException {
        if (Main.curTextWindow == null) return;
        String file = Main.curTextWindow.getTitle();
        if (!new File(file).exists()) {
            System.out.println("Please specify file name");
            return;
        }
        save(file);
    }

    public static String readText(InputStream in) throws IOException {
        LineNumberReader is = new LineNumberReader(new InputStreamReader(new BufferedInputStream(in)));
        String text = "";
        String line;
        while ((line = is.readLine()) != null) {
            text += line + '\n';
        }
        is.close();
        return text;
    }

    public static String readText(String file) throws IOException {
        return readText(new FileInputStream(file));
    }

    public static void open(String file) throws IOException {
        String text;

        InputStream in = Kernel.getInputStream(file);
        if (in == null) return;

        Main.TextWindow w = Main.system.createText();
        text = readText(in);
        w.setTitle(file);
        w.getPad().getEditor().setText(text);
    }

    public static void compile() throws Exception {
        if (Main.curTextWindow == null) return;
        String file = Main.curTextWindow.getTitle();
        if (!new File(file).exists()) {
            System.out.println("Please save file before compile");
            return;
        }
        save(file);
        if (com.sun.tools.javac.Main.compile(new String[]{file}) == 0) {
            System.out.println("Successfully compiled");
            String clazz = file.substring(0, file.lastIndexOf('.'));
//            FileInputStream fis = new FileInputStream (clazz + ".class");
//            byte buf [] = new byte [fis.available()];
//            fis.read (buf);
//            fis.close();
//            MyPreMain.instrument.redefineClasses(new ClassDefinition(Class.forName(clazz), buf));
            MethodHandle.addClass(clazz);
        }
    }

    public static void executeScript(String fileName) {
        try {
            FileInputStream fstream = new FileInputStream(fileName);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String command;
            //Read File Line By Line
            while ((command = br.readLine()) != null)   {
                Kernel.executeCommand(command);
            }
        } catch (IOException io) {
            System.err.println("IOException: " + io.getMessage());
        }
    }
}
