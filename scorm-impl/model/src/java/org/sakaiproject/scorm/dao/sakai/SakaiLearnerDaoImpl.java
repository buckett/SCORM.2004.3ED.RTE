package org.sakaiproject.scorm.dao.sakai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.authz.api.GroupProvider;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.Enrollment;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Membership;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.scorm.dao.LearnerDao;
import org.sakaiproject.scorm.exceptions.LearnerNotDefinedException;
import org.sakaiproject.scorm.model.api.Learner;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

public abstract class SakaiLearnerDaoImpl implements LearnerDao {

	private static Log log = LogFactory.getLog(SakaiLearnerDaoImpl.class);

	private Learner addLearner(String userId, User user, Map<String, Learner> learnerMap) {
		Learner learner;

		if (learnerMap.containsKey(userId)) {
			learner = learnerMap.get(userId);
		} else {
			learner = new Learner(userId);
		}

		learner.setDisplayId(user.getDisplayId());
		learner.setDisplayName(user.getDisplayName());
		learner.setSortName(user.getSortName());

		ResourceProperties resprops = user.getProperties();
		Properties props = new Properties();

		for (Iterator<String> it = resprops.getPropertyNames(); it.hasNext();) {
			String name = it.next();
			Object value = resprops.get(name);
			props.put(name, value);
		}

		learnerMap.put(userId, learner);

		return learner;
	}

	private void addLearnersFromEnrollmentSet(Map<String, Learner> learnerMap, AuthzGroup realm, String providerCourseEid, EnrollmentSet enrollmentSet) {
		if (enrollmentSet != null) {
			Set<Enrollment> enrollments = cms().getEnrollments(enrollmentSet.getEid());
			for (Enrollment e : enrollments) {
				try {
					User user = userDirectoryService().getUserByEid(e.getUserId());
					String userId = user.getId();
					Member member = realm.getMember(userId);
					if (member != null && member.isProvided()) {
						try {
							addLearner(userId, user, learnerMap);
						} catch (Exception ee) {
							log.warn("Unable to add learner from enrollment " + userId, ee);
						}
					}
				} catch (UserNotDefinedException exception) {
					// deal with missing user quietly without throwing a
					// warning message
					log.warn("Failed to find user with id " + e.getUserId(), exception);
				}
			}
		}
	}

	private void addLearnersFromMemberships(Map<String, Learner> learnerMap, AuthzGroup realm, String providerCourseEid, Set<Membership> memberships) {
		if (memberships != null) {
			for (Membership m : memberships) {
				try {
					User user = userDirectoryService().getUserByEid(m.getUserId());
					String userId = user.getId();
					Member member = realm.getMember(userId);
					if (member != null && member.isProvided()) {
						addLearner(userId, user, learnerMap);
					}
				} catch (UserNotDefinedException exception) {
					// deal with missing user quietly without throwing a
					// warning message
					log.warn("Failed to find user with id " + m.getUserId(), exception);
				}
			}
		}
	}

	protected abstract CourseManagementService cms();

	public List<Learner> find(String context) {
		String realmId = siteService().siteReference(context);

		Map<String, Learner> learnerMap = new ConcurrentHashMap<String, Learner>();
		try {
			AuthzGroup realm = ComponentManager.get( AuthzGroupService.class ).getAuthzGroup(realmId);
			String providerGroupId = realm.getProviderGroupId();

			List<String> providerCourseList = getProviderCourseList(StringUtils.trimToNull(providerGroupId));

			// iterate through the provider list first
			for (String providerCourseEid : providerCourseList) {
				if (cms().isSectionDefined(providerCourseEid)) {
					// in case of Section eid
					EnrollmentSet enrollmentSet = cms().getSection(providerCourseEid).getEnrollmentSet();
					addLearnersFromEnrollmentSet(learnerMap, realm, providerCourseEid, enrollmentSet);
					// add memberships
					Set<Membership> memberships = cms().getSectionMemberships(providerCourseEid);
					addLearnersFromMemberships(learnerMap, realm, providerCourseEid, memberships);
				}
			}

			// now for those not provided users
			Set<Member> members = realm.getMembers();
			for (Member member : members) {
				if (!member.isProvided() && member.isActive()) {
					try {
						User user = userDirectoryService().getUserByEid(member.getUserEid());
						String userId = user.getId();
						addLearner(userId, user, learnerMap);

					} catch (UserNotDefinedException e) {
						// deal with missing user quietly without throwing a warning message
						log.warn("Couldn't find user '" + member.getUserEid() + "' while looping over members of " + realm.getReference());
					}
				}
			}

		} catch (GroupNotDefinedException ee) {
			log.warn(this + "  IdUnusedException " + realmId);
		}

		return new ArrayList<Learner>(learnerMap.values());
	}

	private List<String> getProviderCourseList(String id) {
		List<String> rv = new ArrayList<String>();
		if (StringUtils.isEmpty(id))
			return rv;
		// Break Provider Id into course id parts
		String[] courseIds = groupProvider().unpackId(id);

		// Iterate through course ids
		for (String courseId : courseIds) {
			rv.add(courseId);
		}
		return rv;
	}

	protected abstract GroupProvider groupProvider();

	public Learner load(String id) throws LearnerNotDefinedException {
		Learner learner = null;

		try {
			User user = userDirectoryService().getUser(id);

			learner = new Learner(id, user.getDisplayName(), user.getDisplayId());
			learner.setSortName(user.getSortName());

			ResourceProperties resprops = user.getProperties();
			Properties props = new Properties();

			for (Iterator<String> it = resprops.getPropertyNames(); it.hasNext();) {
				String name = it.next();
				Object value = resprops.get(name);
				props.put(name, value);
			}

		} catch (UserNotDefinedException e) {
			throw new LearnerNotDefinedException("There is no learner in the lms with this id " + id);
		}

		return learner;
	}

	protected abstract SiteService siteService();

	protected abstract UserDirectoryService userDirectoryService();
}
