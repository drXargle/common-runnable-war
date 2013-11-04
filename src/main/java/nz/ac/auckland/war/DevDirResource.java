package nz.ac.auckland.war;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;

import java.util.Arrays;
import java.util.HashSet;

public class DevDirResource extends ResourceCollection {
  public DevDirResource(Resource... resources) {
    super(resources);
  }

  /**
   * this is different from the base in that it removes .svn directories from it
   * @return
   */
  @Override
  public String[] list() {
    HashSet<String> set = new HashSet<String>();

    for (Resource r : getResources()) {
      for (String s : r.list()) {
        if (!s.startsWith(".svn"))
          set.add(s);
      }
    }

    String[] result = set.toArray(new String[set.size()]);

    Arrays.sort(result);

    return result;
  }
}
