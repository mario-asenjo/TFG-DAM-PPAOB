import { useEffect, useState } from "react";

function normalizeHash(hash) {
  if (!hash || hash === "#") {
    return "login";
  }
  return hash.replace(/^#\/?/, "") || "login";
}

export function useHashRoute() {
  const [route, setRoute] = useState(normalizeHash(window.location.hash));

  useEffect(() => {
    function onHashChange() {
      setRoute(normalizeHash(window.location.hash));
    }

    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  function navigate(nextRoute) {
    window.location.hash = `#/${nextRoute}`;
  }

  return { route, navigate };
}
