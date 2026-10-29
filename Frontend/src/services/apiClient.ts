import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || "http://localhost:8080/api";

export const apiClient = axios.create({ baseURL: API_BASE_URL });
export { axios };
