# process-tree-killer

[![Build Status](http://jenkins.activeeon.com/job/process-tree-killer/badge/icon)](http://jenkins.activeeon.com/job/process-tree-killer/)

Process Tree Killer for Java, extracted from Jenkins (commit 9c443c8d5bafd63fce574f6d0cf400cd8fe1f124)

The trick that is used to kill a process including its children is to tag them using an environment variable.

## Usage

```java
ProcessBuilder pb = new ProcessBuilder("sleep", "10000");
pb.environment().put("PTK_COOKIE", "killme");
    
Process process = pb.start();
    
ProcessTree.get().killAll(singletonMap("PTK_COOKIE", "killme"));
```
## Acknowledgements

Thanks to [Jenkins](https://github.com/jenkinsci/jenkins) and Kohsuke Kawaguchi.
