import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.viz.DotUtil;

import java.io.*;
import java.util.*;

public class MyTestController {
    static ArrayList<String> classFilePaths = new ArrayList<String>();

    public static void main(String[] args) throws IOException, WalaException, CancelException {
        final File exclusionsFile = new File("src/main/resources/exclusions.txt");
        final String dotFilename = "dependencies.dot";
        FileWriter fw = new FileWriter(dotFilename, false);
        fw.write("digraph \"DirectedGraph\" {\n");
        fw.write("graph [concentrate = true];center=true;fontsize=6;node [ color=blue,shape=\"box\"fontsize=6,fontcolor=black,fontname=Arial];edge [ color=black,fontsize=6,fontcolor=black,fontname=Arial];\n");
        fw.flush();

        getClassPaths(args[1]);
        StringBuilder classFilepath = new StringBuilder();
        for(int i = 0; i < classFilePaths.size(); i++){
            classFilepath.append(classFilePaths.get(i));
            if(i != classFilePaths.size() - 1){
                classFilepath.append(File.pathSeparator);
            }
        }

        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(classFilepath.toString(), exclusionsFile);
        //System.out.println(scope);
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);

        if(args[0].charAt(1) == 'c'){
            CHACallGraph chaCG = new CHACallGraph(cha);
            chaCG.init(new AllApplicationEntrypoints(scope, cha));

            ArrayList<String> iClasses = new ArrayList<String>();
            for(IClass iClass: cha){
                if(scope.isApplicationLoader(iClass.getClassLoader())){
                    iClasses.add(iClass.getName().toString());
                }
            }

            ArrayList<CGNode> appNodes = new ArrayList<CGNode>();
            for (CGNode cgNode: chaCG) {
                if(cgNode.getMethod().toString().startsWith("< Application"))
                    appNodes.add(cgNode);
            }

            HashMap<String, ArrayList<String>> dependencies = new HashMap<String, ArrayList<String>>();
            for (CGNode parentNode: appNodes) {
                Iterator<CGNode> nodeIterator = chaCG.getSuccNodes(parentNode);
                while(nodeIterator.hasNext()){
                    CGNode childNode = nodeIterator.next();
                    for (String classname: iClasses) {
                        if(childNode.toString().contains(classname.concat(","))){
                            String parent = parentNode.toString().split(",")[1].substring(1);
                            String child = childNode.toString().split(",")[1].substring(1);
                            if(dependencies.get(parent) != null){
                                if(!dependencies.get(parent).contains(child)){
                                    dependencies.get(parent).add(child);
                                }
                            }
                            else {
                                dependencies.put(parent, new ArrayList<String>());
                                dependencies.get(parent).add(child);
                            }
                        }
                    }
                }
            }
            for (String key: dependencies.keySet()) {
                for (String item: dependencies.get(key)) {
                    fw.write("\"" + key + "\"->\"" + item + "\"\n");
                }
                fw.flush();
            }
            fw.write("}\n");
            fw.flush();
            fw.close();

            BufferedReader iReader = new BufferedReader(new FileReader(new File(args[2])));
            FileWriter selection = new FileWriter("selection-class.txt");
            String temp = "";
            ArrayList<String> selectedClass = new ArrayList<String>();
            HashMap<String, Boolean> visited = new HashMap<String, Boolean>();
            for(String key: dependencies.keySet()){
                visited.put(key, false);
            }
            while ((temp = iReader.readLine()) != null){
                String classname = temp.split(" ")[0];
                selectClass(classname, dependencies, selectedClass, visited);
            }
            for(CGNode cgNode: appNodes){
                for(String classname: selectedClass){
                    if(cgNode.toString().contains(classname + ",") && !cgNode.toString().contains("<init>") && !cgNode.toString().contains("initialize()")){
                        selection.write(cgNode.getMethod().toString().split(" ")[2].replace(',', ' ') +
                                cgNode.getMethod().toString().split(" ")[2].replace(',', '.').replaceAll("/", ".").substring(1) +
                                cgNode.getMethod().toString().split(" ")[3] +
                                "\n");
                    }
                }
                selection.flush();
            }
            selection.close();
        } else if(args[0].charAt(1) == 'm'){
            AllApplicationEntrypoints entryPoints = new AllApplicationEntrypoints(scope, cha);
            AnalysisOptions options = new AnalysisOptions(scope, entryPoints);
            SSAPropagationCallGraphBuilder builder = Util.makeZeroCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);

            CallGraph cg = builder.makeCallGraph(options);

            ArrayList<CGNode> appNodes = new ArrayList<CGNode>();
            for (CGNode cgNode: cg) {
                if(cgNode.getMethod().toString().startsWith("< Application"))
                    appNodes.add(cgNode);
            }

            HashMap<String, ArrayList<String>> dependencies = new HashMap<String, ArrayList<String>>();
            for (CGNode parentNode: appNodes) {
                Iterator<CGNode> nodeIterator = cg.getSuccNodes(parentNode);
                while(nodeIterator.hasNext()){
                    CGNode childNode = nodeIterator.next();
                    if(childNode.getMethod().toString().startsWith("< Application")){
                        String parentMethod = parentNode.getMethod().toString().split(" ")[2].replace(',', '.').replaceAll("/", ".").substring(1) +
                                parentNode.getMethod().toString().split(" ")[3];
                        String childMethod = childNode.getMethod().toString().split(" ")[2].replace(',', '.').replaceAll("/", ".").substring(1) +
                                childNode.getMethod().toString().split(" ")[3];
                        if(dependencies.get(parentMethod) != null){
                            if(!dependencies.get(parentMethod).contains(childMethod)){
                                dependencies.get(parentMethod).add(childMethod);
                            }
                        }
                        else {
                            dependencies.put(parentMethod, new ArrayList<String>());
                            dependencies.get(parentMethod).add(childMethod);
                        }
                    }
                }
            }
            for (String key: dependencies.keySet()) {
                for (String item: dependencies.get(key)) {
                    fw.write("\"" + key + "\"->\"" + item + "\"\n");
                }
                fw.flush();
            }
            fw.write("}\n");
            fw.flush();
            fw.close();

            BufferedReader iReader = new BufferedReader(new FileReader(new File(args[2])));
            FileWriter selection = new FileWriter("selection-method.txt");
            String temp = "";
            ArrayList<String> selectedMethod = new ArrayList<String>();
            HashMap<String, Boolean> visited = new HashMap<String, Boolean>();
            for(String key: dependencies.keySet()){
                visited.put(key, false);
            }
            while ((temp = iReader.readLine()) != null){
                String methodName = temp.split(" ")[1];
                selectMethod(methodName, dependencies, selectedMethod, visited);
            }

            for(String methodName: selectedMethod){
                if(!methodName.contains("<init>")) {
                    StringBuilder sBuilder = new StringBuilder("L");
                    for (int i = 0; i < methodName.split("\\.").length - 1; i++) {
                        sBuilder.append(methodName.split("\\.")[i]);
                        if (i != methodName.split("\\.").length - 2) {
                            sBuilder.append('/');
                        }
                    }
                    selection.write(sBuilder.toString() + " " + methodName + "\n");
                }
            }
            selection.flush();
            selection.close();
        }
    }

    static void selectClass(String classname, HashMap<String, ArrayList<String>> dependencies, ArrayList<String> selectedClass, HashMap<String, Boolean> visited) {
        for(String key: dependencies.keySet()){
            if(dependencies.get(key).contains(classname) && !selectedClass.contains(key)){
                if(key.contains("Test")) {
                    selectedClass.add(key);
                }
                else {
                    if(!visited.get(key)) {
                        visited.put(key, true);
                        selectClass(key, dependencies, selectedClass, visited);
                    }
                }
            }
        }
    }

    static void selectMethod(String methodName, HashMap<String, ArrayList<String>> dependencies, ArrayList<String> selectedMethod, HashMap<String, Boolean> visited) {
        for(String key: dependencies.keySet()){
            if(dependencies.get(key).contains(methodName) && !selectedMethod.contains(key)){
                if(key.contains("Test")) {
                    selectedMethod.add(key);
                }
                else {
                    if(!visited.get(key)) {
                        visited.put(key, true);
                        selectMethod(key, dependencies, selectedMethod, visited);
                    }
                }
            }
        }
    }

    static void getClassPaths(String basePath){
        File file = new File(basePath);
        if(file.isDirectory()){
            String[] filenameList = file.list();
            assert filenameList != null;
            for(String filename: filenameList){
                getClassPaths(basePath + "/" + filename);
            }
        }
        else {
            if(file.getName().endsWith(".class"))
                classFilePaths.add(file.getAbsolutePath());
        }
    }
}
