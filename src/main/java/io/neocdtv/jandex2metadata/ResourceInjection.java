package io.neocdtv.jandex2metadata;

public class ResourceInjection {
  private String name;
  private String lookup;
  private String type;
  private String injectionTargetClass;
  private String injectionTargetName;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLookup() {
    return lookup;
  }

  public void setLookup(String lookup) {
    this.lookup = lookup;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getInjectionTargetClass() {
    return injectionTargetClass;
  }

  public void setInjectionTargetClass(String injectionTargetClass) {
    this.injectionTargetClass = injectionTargetClass;
  }

  public String getInjectionTargetName() {
    return injectionTargetName;
  }

  public void setInjectionTargetName(String injectionTargetName) {
    this.injectionTargetName = injectionTargetName;
  }
}