package org.sakaiproject.scorm.dao.hibernate;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.sakaiproject.scorm.dao.api.AttemptDao;
import org.sakaiproject.scorm.model.api.Attempt;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

public class AttemptDaoImpl extends HibernateDaoSupport implements AttemptDao {

	public int count(final long contentPackageId, final String learnerId) {
		HibernateCallback hcb = new HibernateCallback() {
			public Object doInHibernate(Session session) throws HibernateException, SQLException {
				Criteria criteria = session.createCriteria(Attempt.class)
						.add(Restrictions.eq("contentPackageId", contentPackageId))
				        .add(Restrictions.eq("learnerId", learnerId))
				        .setProjection(Projections.count("id"));
				return criteria.uniqueResult();
			}
		};

		Object result = getHibernateTemplate().execute(hcb);

		int r = 0;
		if (result != null) {
			if (result instanceof Number) {
				r = ((Number) result).intValue();
			}
		}
		return r;
	}

	@SuppressWarnings("unchecked")
    public List<Attempt> find(long contentPackageId) {
		return (List<Attempt>) getHibernateTemplate().find("from " + Attempt.class.getName() + " where contentPackageId=? ", new Object[] { contentPackageId });
	}

	@SuppressWarnings("unchecked")
	public List<Attempt> find(long contentPackageId, String learnerId) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("from ").append(Attempt.class.getName()).append(" where contentPackageId=? and learnerId=? order by attemptNumber desc");

		return (List<Attempt>) getHibernateTemplate().find(buffer.toString(), new Object[] { contentPackageId, learnerId });
	}

	@SuppressWarnings("unchecked")
	public List<Attempt> find(String courseId, String learnerId) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("from ").append(Attempt.class.getName()).append(" where courseId=? and learnerId=? order by attemptNumber desc");
		return (List<Attempt>) getHibernateTemplate().find(buffer.toString(), new Object[] { courseId, learnerId });
	}

	
	public Attempt find(String courseId, String learnerId, long attemptNumber) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("from ").append(Attempt.class.getName()).append(" where courseId=? and learnerId=? and attemptNumber=? ");
		
		@SuppressWarnings("unchecked")
		List<Attempt> r = (List<Attempt>) getHibernateTemplate().find(buffer.toString(), new Object[] { courseId, learnerId, attemptNumber });
		if (r.isEmpty())
		{
			return null;
		}

		return (Attempt) r.get(r.size() - 1);
	}

	public Attempt load(long id) {
		return (Attempt) getHibernateTemplate().load(Attempt.class, id);
	}

	public Attempt lookup(final long contentPackageId, final String learnerId, final long attemptNumber) {
		HibernateCallback hcb = new HibernateCallback() {
			public Object doInHibernate(Session session) throws HibernateException, SQLException {
				StringBuilder buffer = new StringBuilder();
				buffer.append("from ").append(Attempt.class.getName()).append(" where contentPackageId=? and learnerId=? and attemptNumber=?");

				Query query = session.createQuery(buffer.toString());
				query.setLong(0, contentPackageId);
				query.setString(1, learnerId);
				query.setLong(2, attemptNumber);

				return query.uniqueResult();
			}
		};

		Attempt attempt = (Attempt) getHibernateTemplate().execute(hcb);

		return attempt;
	}

	public void save(Attempt attempt) {
		attempt.setLastModifiedDate(new Date());
		getHibernateTemplate().saveOrUpdate(attempt);
	}

	public Attempt lookupNewest(long contentPackageId, String learnerId) {
		// First figure out the highest attempt nr..
		DetachedCriteria sub = DetachedCriteria.forClass(Attempt.class)
				.add(Restrictions.eq("contentPackageId", contentPackageId))
		        .add(Restrictions.eq("learnerId", learnerId))
		        .setProjection(Projections.max("attemptNumber"));
		// Than use it as restriction
		DetachedCriteria criteria = DetachedCriteria.forClass(Attempt.class)
				.add(Restrictions.eq("contentPackageId", contentPackageId))
		        .add(Restrictions.eq("learnerId", learnerId))
		        .add(Subqueries.propertyEq("attemptNumber", sub));

		return uniqueResult(criteria);
	}

	protected Attempt uniqueResult(final DetachedCriteria criteria) {
	    return (Attempt)getHibernateTemplate().execute(new HibernateCallback() {
			
			public Object doInHibernate(Session session) throws HibernateException, SQLException {
				Criteria s = criteria.getExecutableCriteria(session);
				return s.uniqueResult();
			}
		});
    }

}
