package io.neocdtv.jandex2metadata;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
  public final static Set<String> SESSION_STATELESS_BEANS = new HashSet<>();
  public final static Set<String> SESSION_SINGLETON_BEANS = new HashSet<>();
  public final static Set<String> SESSION_STATEFUL_BEANS = new HashSet<>();
  public final static Set<ResourceInjection> RESOURCE_INJECTION = new HashSet<>();
  public final static List<String> SESSION_STATELESS_BEANS_ANNOTATIONS = Arrays.asList(
      "javax.ejb.Stateless");
  public final static List<String> SESSION_SINGLETON_BEANS_ANNOTATIONS = Arrays.asList(
      "javax.ejb.Singleton");
  public final static List<String> SESSION_STATEFUL_BEANS_ANNOTATIONS = Arrays.asList(
      "javax.ejb.Stateful");

  private static final boolean DEBUG = false;

  private final static String ARG_NAME_DIR = "dir";
  private final static String ARG_NAME_IDX = "idx";

  public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException {

    Index index = null;
    final String dir = CliUtil.findCommandArgumentByName(ARG_NAME_DIR, args);
    final String idx = CliUtil.findCommandArgumentByName(ARG_NAME_IDX, args);
    if (dir != null) {
      index = buildIndex(dir);
    } else if (idx != null) {
      index = readIndex(idx);
    } else {
      usage();
      System.exit(1);
    }

    printClasses(index);
    findEJBs(index);
    findResourceInjection(index);
    generateEjbJar();
    generateResourceWebXml();
  }

  private static void generateResourceWebXml() {
    RESOURCE_INJECTION.forEach(resourceInjection -> {
      System.out.println("  <resource-env-ref>");
      System.out.println(String.format("    <resource-env-ref-name>%s</resource-env-ref-name>", resourceInjection.getName()));
      System.out.println(String.format("    <resource-env-ref-type>%s</resource-env-ref-type>", resourceInjection.getType()));

      System.out.println("    <injection-target>");
      System.out.println(String.format("      <injection-target-class>%s</injection-target-class>", resourceInjection.getInjectionTargetClass()));
      System.out.println(String.format("      <injection-target-name>%s</injection-target-name>", resourceInjection.getInjectionTargetName()));
      System.out.println("    </injection-target>");
      if (resourceInjection.getLookup() != null) {
        System.out.println(String.format("  <lookup-name>%s</lookup-name>", resourceInjection.getLookup()));
      }
      System.out.println("  </resource-env-ref>");
    });
  }

  private static void generateEjbJar() {
    System.out.println("  <enterprise-beans>");
    SESSION_STATELESS_BEANS.forEach(bean -> {
      System.out.println("    <session>");
      System.out.println(String.format("      <ejb-name>%s</ejb-name>", getClassName(bean)));
      System.out.println("      <local-bean/>");
      System.out.println(String.format("      <ejb-class>%s</ejb-class>", bean));
      System.out.println(String.format("      <session-type>Stateless</session-type>", bean));
      System.out.println("    </session>");
    });

    SESSION_SINGLETON_BEANS.forEach(bean -> {
      System.out.println("    <session>");
      System.out.println(String.format("      <ejb-name>%s</ejb-name>", getClassName(bean)));
      System.out.println("      <local-bean/>");
      System.out.println(String.format("      <ejb-class>%s</ejb-class>", bean));
      System.out.println(String.format("      <session-type>Singleton</session-type>", bean));
      System.out.println("    </session>");
    });

    SESSION_STATEFUL_BEANS.forEach(bean -> {
      System.out.println("    <session>");
      System.out.println(String.format("      <ejb-name>%s</ejb-name>", getClassName(bean)));
      System.out.println("      <local-bean/>");
      System.out.println(String.format("      <ejb-class>%s</ejb-class>", bean));
      System.out.println(String.format("      <session-type>Stateful</session-type>", bean));
      System.out.println("    </session>");
    });
    System.out.println("  </enterprise-beans>");
  }

  private static String getClassName(final String fqName) {
    final String[] split = fqName.split("\\.");
    return split[split.length - 1];
  }

  // TODO: use it
  private static void writeXml(String beansXmlPath, Element document) throws TransformerException, XPathExpressionException {
    XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//text()[normalize-space(.) = '']");
    NodeList blankTextNodes = (NodeList) xpath.evaluate(document, XPathConstants.NODESET);

    for (int i = 0; i < blankTextNodes.getLength(); i++) {
      blankTextNodes.item(i).getParentNode().removeChild(blankTextNodes.item(i));
    }

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
    DOMSource domSource = new DOMSource(document);
    StreamResult streamResult = new StreamResult(new File(beansXmlPath));
    transformer.transform(domSource, streamResult);
  }

  private static Document readXml(String beansXmlPath) throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    return dBuilder.parse(new File(beansXmlPath));
  }

  private static Index buildIndex(final String pathToScan) throws IOException {
    Indexer indexer = new Indexer();
    collectClasses(pathToScan)
        .forEach(path -> {
          final String absolutePath = path.toFile().getAbsolutePath();
          try {
            final FileInputStream fileInputStream = new FileInputStream(absolutePath);
            indexer.index(fileInputStream);
          } catch (IOException e) {
            System.err.println("Can't process " + absolutePath);
          }
        });
    return indexer.complete();
  }

  private static Index readIndex(final String indexPath) throws IOException {
    IndexReader reader = new IndexReader(new FileInputStream(indexPath));
    return reader.read();
  }

  private static Stream<Path> collectClasses(final String pathToScan) throws IOException {
    return Files.walk(Paths.get(pathToScan))
        .filter(path -> path.toString().endsWith("class"));
  }

  private static void usage() {
    System.out.println("Usage:");
    System.out.println("  java -jar target/java -jar target/jandex-2-cdi12-scan-exclude-list.jar dir|idx [beansXml]");
    System.out.println("Examples:");
    System.out.println("1. read existing jandex index and output scan node to std out");
    System.out.println("  java -jar target/java -jar target/jandex-2-metadata-complete-true.jar idx=input_path_to_jandex_idx");
    System.out.println("2. build jandex from compiled classes and output scan node to std out");
    System.out.println("  java -jar target/java -jar target/jandex-2-cdi12-scan-exclude-list.jar dir=input_path_to_classes_dir");
  }

  private static void printClasses(Index index) {
    System.out.println("CLASSES: ");
    for (ClassInfo classInfo : index.getKnownClasses()) {
      System.out.println("  CLASS: " + classInfo.toString() + ", " + classInfo.nestingType());
    }

  }

  public static void findEJBs(final Index index) {
    System.out.println("SESSION_STATELESS_BEANS: ");
    for (String annotations : SESSION_STATELESS_BEANS_ANNOTATIONS) {
      DotName annotation = DotName.createSimple(annotations);
      for (AnnotationInstance annotationInstance : index.getAnnotations(annotation)) {
        final String name = annotationInstance.target().toString();
        SESSION_STATELESS_BEANS.add(name);
        System.out.println("  SESSION_STATELESS_BEAN: " + name);
      }
    }
    System.out.println("SESSION_SINGLETON_BEANS: ");
    for (String annotations : SESSION_SINGLETON_BEANS_ANNOTATIONS) {
      DotName annotation = DotName.createSimple(annotations);
      for (AnnotationInstance annotationInstance : index.getAnnotations(annotation)) {
        final String name = annotationInstance.target().toString();
        SESSION_SINGLETON_BEANS.add(name);
        System.out.println("  SESSION_SINGLETON_BEAN: " + name);
      }
    }
    System.out.println("SESSION_STATEFUL_BEANS: ");
    for (String annotations : SESSION_STATEFUL_BEANS_ANNOTATIONS) {
      DotName annotation = DotName.createSimple(annotations);
      for (AnnotationInstance annotationInstance : index.getAnnotations(annotation)) {
        final String name = annotationInstance.target().toString();
        SESSION_STATEFUL_BEANS.add(name);
        System.out.println("  SESSION_STATEFUL_BEAN: " + name);
      }
    }
  }

  public static void findResourceInjection(final Index index) {
    System.out.println("RESOURCE INJECTIONS: ");
    DotName inject = DotName.createSimple("javax.annotation.Resource");
    final List<AnnotationInstance> annotations = index.getAnnotations(inject);
    for (AnnotationInstance annotation : annotations) {
      final AnnotationTarget target = annotation.target();
      switch (target.kind()) {
        case FIELD:
          final FieldInfo fieldInfo = target.asField();
          final ClassInfo injectionTarget = fieldInfo.declaringClass();
          final String targetName = injectionTarget.toString();

          final ResourceInjection resourceInjection = new ResourceInjection();
          final Type type = fieldInfo.type();

          resourceInjection.setName(targetName +"/" + fieldInfo.name());
          resourceInjection.setType(type.toString());
          resourceInjection.setInjectionTargetClass(targetName);
          resourceInjection.setInjectionTargetName(fieldInfo.name());

          final AnnotationValue lookup = annotation.value("lookup");
          if (lookup != null) {
            resourceInjection.setLookup(lookup.asString());
          }
          RESOURCE_INJECTION.add(resourceInjection);
          break;
        default:
          System.out.println("not expected this");
      }
    }
  }

}

