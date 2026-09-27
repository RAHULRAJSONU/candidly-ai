package ai.candidly.career.discovery;

/**
 * One Workday-hosted careers site to poll. Unlike Greenhouse/Lever/Ashby (one fixed
 * global host, company identified by a single slug), each Workday tenant is served from
 * its own numbered pod hostname (e.g. {@code wd1}/{@code wd5}/{@code wd12}...), so there
 * is no single endpoint pattern - a site has to be configured with its own
 * {@code hostname}/{@code tenant}/{@code site} triple, found by inspecting the company's
 * own myworkdayjobs.com careers URL (the browser URL's first path segment after the
 * locale is {@code site}; the subdomain before {@code .myworkdayjobs.com} up to the pod
 * suffix is {@code tenant}).
 */
public class WorkdaySiteConfig {

    private String hostname;
    private String tenant;
    private String site;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }
}
