async function myFetch(url, options = {}) {
    const defaultOptions = {
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        ...options
    };

    try {
        const response = await fetch(url, defaultOptions);

        // 处理 401：不再仅仅是 console.warn，而是确保干净地跳转
        if (response.status === 401) {
            const currentPath = encodeURIComponent(window.location.href);
            // 只要这一行执行，浏览器就会开始加载新页面
            window.location.href = `/login.html?redirect=${currentPath}`;

            // 返回一个挂起的 Promise，防止后面的 .then() 或业务逻辑继续报错
            return new Promise(() => {});
        }

        // 处理其他非 200 状态
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.errorMsg || `请求失败: ${response.status}`);
        }

        // 针对流式接口的特殊处理
        if (url.includes('/chat')) {
            return response;
        }

        return await response.json();
    } catch (error) {
        // 如果是手动中止（Abort）则不报错，否则弹出
        if (error.name !== 'AbortError') {
            console.error("Fetch Error:", error);
            throw error;
        }
    }
}