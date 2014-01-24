package models;

import com.avaje.ebean.annotation.Transactional;
import controllers.UserApp;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.GlobalResource;
import models.resource.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import utils.AccessControl;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class Watch extends UserAction {
    private static final long serialVersionUID = 1L;

    public static Finder<Long, Watch> find = new Finder<>(Long.class, Watch.class);

    public static List<Watch> findBy(ResourceType resourceType, String resourceId) {
        return findBy(find, resourceType, resourceId);
    }

    public static Watch findBy(User watcher, ResourceType resourceType, String resourceId) {
        return findBy(find, watcher, resourceType, resourceId);
    }

    public static List<Watch> findBy(User user, ResourceType resourceType) {
        return findBy(find, user, resourceType);
    }

    public static int countBy(ResourceType type, String id) {
        return countBy(find, type, id);
    }

    public static void watch(Resource resource) {
        watch(UserApp.currentUser(), resource);
    }

    @Transactional
    public static void watch(User user, Resource resource) {
        watch(user, resource.getType(), resource.getId());
    }

    public static void watch(User user, ResourceType resourceType, String resourceId) {
        Watch watch = Watch.findBy(user, resourceType, resourceId);
        if (watch == null) {
            watch = new Watch();
            watch.user = user;
            watch.resourceId = resourceId;
            watch.resourceType = resourceType;
            watch.save();
        }

        Unwatch unwatch = Unwatch.findBy(user, resourceType, resourceId);
        if (unwatch != null) {
            unwatch.delete();
        }
    }

    public static void unwatch(Resource resource) {
        unwatch(UserApp.currentUser(), resource);
    }

    public static void unwatch(User user, Resource resource) {
        unwatch(user, resource.getType(), resource.getId());
    }

    public static void unwatch(User user, ResourceType resourceType, String resourceId) {
        Unwatch unwatch = Unwatch.findBy(user, resourceType, resourceId);
        if (unwatch == null) {
            unwatch = new Unwatch();
            unwatch.user = user;
            unwatch.resourceId = resourceId;
            unwatch.resourceType = resourceType;
            unwatch.save();
        }

        Watch watch = Watch.findBy(user, resourceType, resourceId);
        if (watch != null) {
            watch.delete();
        }
    }

    public static Set<User> findWatchers(Resource target) {
        return findWatchers(target.getType(), target.getId());
    }

    public static Set<User> findWatchers(ResourceType resourceType, String resourceId) {
        HashSet<User> users = new HashSet<>();
        for (Watch watch: Watch.findBy(resourceType, resourceId)) {
            users.add(watch.user);
        }
        return users;
    }

    public static Set<User> findUnwatchers(Resource target) {
        return findUnwatchers(target.getType(), target.getId());
    }

    public static Set<User> findUnwatchers(ResourceType resourceType, String resourceId) {
        HashSet<User> users = new HashSet<>();
        for (Unwatch unwatch: Unwatch.findBy(resourceType, resourceId)) {
            users.add(unwatch.user);
        }
        return users;
    }

    public static List<String> findWatchedResourceIds(User user, ResourceType resourceType) {
        ArrayList<String> resourceIds = new ArrayList<>();
        for (Watch watch: Watch.findBy(user, resourceType)) {
            resourceIds.add(watch.resourceId);
        }
        for (Unwatch unwatch: Unwatch.findBy(user, resourceType)) {
            resourceIds.remove(unwatch.resourceId);
        }
        return resourceIds;
    }

    public static boolean isWatching(User user, ResourceType resourceType, String resourceId) {
        Watch watch = Watch.findBy(user, resourceType, resourceId);
        Unwatch unwatch = Unwatch.findBy(user, resourceType, resourceId);

        return watch != null && unwatch == null;
    }

    public static boolean isWatching(User user, Resource resource) {
        return isWatching(user, resource.getType(), resource.getId());
    }

    public static boolean isWatching(Resource resource) {
        return isWatching(UserApp.currentUser(), resource.getType(), resource.getId());
    }

    /**
     * {@code resource} 를 실질적으로 지켜보는 사용자들을 찾는다.
     * {@code baseWatchers} 와
     * {@code resource} 가 어떤 프로젝트에 속한 것이라면 해당 프로젝트를 지켜보는 사용자,
     * 명시적으로 {@code resource} 를 지켜보는 사용자의 합집합에서
     * 명시적으로 {@code resource} 를 지켜보지 않는 사용자와
     * {@code resource} 에 읽기 권한이 없는 사용자를 제외한 집합을 반환한다.
     *
     * @param baseWatchers 기본적으로 watcher 에 포함 시킬 사용자들
     * @param resource 지켜보는 대상
     * @return {@code resource} 를 실질적으로 지켜보는 사용자들
     */
    public static Set<User> findActualWatchers(final Set<User> baseWatchers, final Resource resource) {
        Set<User> actualWatchers = new HashSet<>();
        actualWatchers.addAll(baseWatchers);

        // Add every user who watches the project to which this resource belongs
        if (!(resource instanceof GlobalResource)) {
            Project project = resource.getProject();
            actualWatchers.addAll(findWatchers(project.asResource()));
        }

        // For this resource, add every user who watch explicitly and remove who unwatch explicitly.
        actualWatchers.addAll(findWatchers(resource));
        actualWatchers.removeAll(findUnwatchers(resource));

        // Filter the watchers who has no permission to read this resource.
        CollectionUtils.filter(actualWatchers, new Predicate() {
            @Override
            public boolean evaluate(Object watcher) {
                return AccessControl.isAllowed((User) watcher, resource, Operation.READ);
            }
        });
        return actualWatchers;
    }

}
